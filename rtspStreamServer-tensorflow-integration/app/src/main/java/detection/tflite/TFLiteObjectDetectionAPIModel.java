/* Copyright 2019 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package detection.tflite;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Trace;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

/**
 * Wrapper for frozen detection models trained using the Tensorflow Object Detection API:
 * github.com/tensorflow/models/tree/master/research/object_detection
 */
public class TFLiteObjectDetectionAPIModel implements Classifier {
  // Only return this many results.
  private static final int NUM_DETECTIONS = 10;
  private static final int EXPECTED_INPUT_SIZE = 300;
  private static final int EXPECTED_LABEL_COUNT = 91;
  private static final int LABEL_OFFSET = 1;
  private static final String ASSET_PREFIX = "file:///android_asset/";
  // Number of threads in the java app
  private static final int NUM_THREADS = 4;
  // Config values.
  private int inputSize;
  // Pre-allocated buffers.
  private Vector<String> labels = new Vector<String>();
  private int[] intValues;
  // outputLocations: array of shape [Batchsize, NUM_DETECTIONS,4]
  // contains the location of detected boxes
  private float[][][] outputLocations;
  // outputClasses: array of shape [Batchsize, NUM_DETECTIONS]
  // contains the classes of detected boxes
  private float[][] outputClasses;
  // outputScores: array of shape [Batchsize, NUM_DETECTIONS]
  // contains the scores of detected boxes
  private float[][] outputScores;
  // numDetections: array of shape [Batchsize]
  // contains the number of detected boxes
  private float[] numDetections;

  private ByteBuffer imgData;

  private Interpreter tfLite;

  private TFLiteObjectDetectionAPIModel() {}

  /** Memory-map the model file in Assets. */
  private static MappedByteBuffer loadModelFile(AssetManager assets, String modelFilename)
      throws IOException {
    try (AssetFileDescriptor fileDescriptor = assets.openFd(modelFilename);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel()) {
      long startOffset = fileDescriptor.getStartOffset();
      long declaredLength = fileDescriptor.getDeclaredLength();
      return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }
  }

  private static List<String> loadLabels(AssetManager assets, String labelFilename)
      throws IOException {
    if (labelFilename == null || labelFilename.trim().isEmpty()) {
      throw new IllegalArgumentException("Label asset path is required");
    }

    final String actualFilename =
        labelFilename.startsWith(ASSET_PREFIX)
            ? labelFilename.substring(ASSET_PREFIX.length())
            : labelFilename;
    if (actualFilename.isEmpty()) {
      throw new IllegalArgumentException("Label asset path is invalid");
    }

    final ArrayList<String> loadedLabels = new ArrayList<>();
    try (InputStream labelsInput = assets.open(actualFilename);
        BufferedReader reader = new BufferedReader(new InputStreamReader(labelsInput))) {
      String line;
      while ((line = reader.readLine()) != null) {
        loadedLabels.add(line);
      }
    }
    validateLabels(loadedLabels);
    return loadedLabels;
  }

  private static void validateLabels(List<String> candidateLabels) {
    if (candidateLabels == null || candidateLabels.size() != EXPECTED_LABEL_COUNT) {
      throw new IllegalArgumentException(
          "Expected exactly " + EXPECTED_LABEL_COUNT + " labels");
    }
    for (int index = 0; index < candidateLabels.size(); index++) {
      final String label = candidateLabels.get(index);
      if (label == null || label.trim().isEmpty()) {
        throw new IllegalArgumentException("Label " + index + " is empty");
      }
    }
  }

  private static void validateTensor(
      String name, Tensor tensor, DataType expectedType, int[] expectedShape) {
    if (tensor.dataType() != expectedType || !Arrays.equals(tensor.shape(), expectedShape)) {
      throw new IllegalArgumentException(
          name
              + " must be "
              + expectedType
              + " "
              + Arrays.toString(expectedShape)
              + ", found "
              + tensor.dataType()
              + " "
              + Arrays.toString(tensor.shape()));
    }
  }

  private void validateModelContract() {
    if (tfLite.getInputTensorCount() != 1) {
      throw new IllegalArgumentException("Expected exactly one model input tensor");
    }
    if (tfLite.getOutputTensorCount() != 4) {
      throw new IllegalArgumentException("Expected exactly four model output tensors");
    }
    validateTensor(
        "Input tensor",
        tfLite.getInputTensor(0),
        DataType.UINT8,
        new int[] {1, EXPECTED_INPUT_SIZE, EXPECTED_INPUT_SIZE, 3});
    validateTensor(
        "Output tensor 0",
        tfLite.getOutputTensor(0),
        DataType.FLOAT32,
        new int[] {1, NUM_DETECTIONS, 4});
    validateTensor(
        "Output tensor 1",
        tfLite.getOutputTensor(1),
        DataType.FLOAT32,
        new int[] {1, NUM_DETECTIONS});
    validateTensor(
        "Output tensor 2",
        tfLite.getOutputTensor(2),
        DataType.FLOAT32,
        new int[] {1, NUM_DETECTIONS});
    validateTensor(
        "Output tensor 3", tfLite.getOutputTensor(3), DataType.FLOAT32, new int[] {1});
  }

  static TFLiteObjectDetectionAPIModel createForTesting(
      ByteBuffer modelBuffer, List<String> candidateLabels, int inputSize, boolean isQuantized) {
    return createFromBuffer(modelBuffer, candidateLabels, inputSize, isQuantized);
  }

  private static TFLiteObjectDetectionAPIModel createFromBuffer(
      ByteBuffer modelBuffer, List<String> candidateLabels, int inputSize, boolean isQuantized) {
    if (modelBuffer == null) {
      throw new IllegalArgumentException("Model buffer is required");
    }
    if (inputSize != EXPECTED_INPUT_SIZE) {
      throw new IllegalArgumentException("Input size must be " + EXPECTED_INPUT_SIZE);
    }
    if (!isQuantized) {
      throw new IllegalArgumentException("The bundled model requires quantized UINT8 input");
    }
    validateLabels(candidateLabels);

    final TFLiteObjectDetectionAPIModel detector = new TFLiteObjectDetectionAPIModel();
    detector.labels.addAll(candidateLabels);
    detector.inputSize = inputSize;
    try {
      final Interpreter.Options options = new Interpreter.Options().setNumThreads(NUM_THREADS);
      detector.tfLite = new Interpreter(modelBuffer, options);
      detector.tfLite.allocateTensors();
      detector.validateModelContract();
    } catch (RuntimeException error) {
      detector.close();
      throw error;
    }

    detector.imgData = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3);
    detector.imgData.order(ByteOrder.nativeOrder());
    detector.intValues = new int[inputSize * inputSize];
    detector.outputLocations = new float[1][NUM_DETECTIONS][4];
    detector.outputClasses = new float[1][NUM_DETECTIONS];
    detector.outputScores = new float[1][NUM_DETECTIONS];
    detector.numDetections = new float[1];
    return detector;
  }

  /**
   * Initializes a native TensorFlow session for classifying images.
   *
   * @param assetManager The asset manager to be used to load assets.
   * @param modelFilename The filepath of the model GraphDef protocol buffer.
   * @param labelFilename The filepath of label file for classes.
   * @param inputSize The size of image input
   * @param isQuantized Boolean representing model is quantized or not
   */
  public static Classifier create(
      final AssetManager assetManager,
      final String modelFilename,
      final String labelFilename,
      final int inputSize,
      final boolean isQuantized)
      throws IOException {
    return createFromBuffer(
        loadModelFile(assetManager, modelFilename),
        loadLabels(assetManager, labelFilename),
        inputSize,
        isQuantized);
  }

  @Override
  public List<Recognition> recognizeImage(final Bitmap bitmap) {
    if (tfLite == null) {
      throw new IllegalStateException("Classifier is closed");
    }
    if (bitmap == null) {
      throw new IllegalArgumentException("Bitmap is required");
    }
    if (bitmap.getWidth() != inputSize || bitmap.getHeight() != inputSize) {
      throw new IllegalArgumentException(
          "Bitmap must be exactly " + inputSize + "x" + inputSize);
    }

    // Log this method so that it can be analyzed with systrace.
    Trace.beginSection("recognizeImage");
    try {
      Trace.beginSection("preprocessBitmap");
      try {
        bitmap.getPixels(intValues, 0, inputSize, 0, 0, inputSize, inputSize);

        imgData.rewind();
        for (int i = 0; i < inputSize; ++i) {
          for (int j = 0; j < inputSize; ++j) {
            int pixelValue = intValues[i * inputSize + j];
            imgData.put((byte) ((pixelValue >> 16) & 0xFF));
            imgData.put((byte) ((pixelValue >> 8) & 0xFF));
            imgData.put((byte) (pixelValue & 0xFF));
          }
        }
      } finally {
        Trace.endSection();
      }

      outputLocations = new float[1][NUM_DETECTIONS][4];
      outputClasses = new float[1][NUM_DETECTIONS];
      outputScores = new float[1][NUM_DETECTIONS];
      numDetections = new float[1];

      final Object[] inputArray = {imgData};
      final Map<Integer, Object> outputMap = new HashMap<>();
      outputMap.put(0, outputLocations);
      outputMap.put(1, outputClasses);
      outputMap.put(2, outputScores);
      outputMap.put(3, numDetections);

      Trace.beginSection("run");
      try {
        tfLite.runForMultipleInputsOutputs(inputArray, outputMap);
      } finally {
        Trace.endSection();
      }

      final float rawDetectionCount = numDetections[0];
      requireFinite(rawDetectionCount, "Detection count");
      if (rawDetectionCount != (int) rawDetectionCount
          || rawDetectionCount < 0
          || rawDetectionCount > NUM_DETECTIONS) {
        throw new IllegalStateException("Invalid detection count: " + rawDetectionCount);
      }

      final int detectionCount = (int) rawDetectionCount;
      final ArrayList<Recognition> recognitions = new ArrayList<>(detectionCount);
      for (int i = 0; i < detectionCount; ++i) {
        for (int coordinate = 0; coordinate < 4; coordinate++) {
          requireFinite(outputLocations[0][i][coordinate], "Detection location");
        }
        requireFinite(outputScores[0][i], "Detection score");
        final RectF detection =
            new RectF(
                outputLocations[0][i][1] * inputSize,
                outputLocations[0][i][0] * inputSize,
                outputLocations[0][i][3] * inputSize,
                outputLocations[0][i][2] * inputSize);
        recognitions.add(
            new Recognition(
                String.valueOf(i),
                labelForClass(outputClasses[0][i]),
                outputScores[0][i],
                detection));
      }
      return recognitions;
    } finally {
      Trace.endSection();
    }
  }

  private static void requireFinite(float value, String name) {
    if (Float.isNaN(value) || Float.isInfinite(value)) {
      throw new IllegalStateException(name + " is not finite");
    }
  }

  String labelForClass(float rawClass) {
    requireFinite(rawClass, "Detection class");
    if (rawClass != (int) rawClass) {
      throw new IllegalStateException("Detection class is not an integer: " + rawClass);
    }
    final int classIndex = (int) rawClass;
    final int labelIndex = classIndex + LABEL_OFFSET;
    if (classIndex < 0 || labelIndex >= labels.size()) {
      throw new IllegalStateException("Detection class is outside the label map: " + rawClass);
    }
    return labels.get(labelIndex);
  }

  @Override
  public void enableStatLogging(final boolean logStats) {}

  @Override
  public String getStatString() {
    return "";
  }

  @Override
  public void close() {
    if (tfLite != null) {
      tfLite.close();
      tfLite = null;
    }
  }
}
