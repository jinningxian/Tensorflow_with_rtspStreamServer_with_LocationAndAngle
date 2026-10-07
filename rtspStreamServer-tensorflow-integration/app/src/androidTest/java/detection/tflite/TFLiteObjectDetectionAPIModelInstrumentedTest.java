package detection.tflite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RectF;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class TFLiteObjectDetectionAPIModelInstrumentedTest {
  private static final int INPUT_SIZE = 300;
  private static final String MODEL_SHA256 =
      "e4b118e5e4531945de2e659742c7c590f7536f8d0ed26d135abcfe83b4779d13";
  private static final String LABELS_SHA256 =
      "c7e79c855f73cbba9f33d649d60e1676eb0a974021a41696d1ac0d4b7f7e0211";

  @Test
  public void packagedAssetsAndCpuInferenceMatchTheFrozenContract() throws Exception {
    final AssetManager assets = targetContext().getAssets();
    assertEquals(MODEL_SHA256, sha256(assets, "detect.tflite"));
    assertEquals(LABELS_SHA256, sha256(assets, "labelmap.txt"));

    final List<String> labels = readLabels(assets);
    assertEquals(91, labels.size());
    final Set<String> knownLabels = new HashSet<>(labels);

    final TFLiteObjectDetectionAPIModel detector = createDetector(assets);
    final Bitmap bitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888);
    bitmap.eraseColor(Color.BLACK);
    try {
      final List<Classifier.Recognition> recognitions = detector.recognizeImage(bitmap);
      assertEquals(10, recognitions.size());
      for (Classifier.Recognition recognition : recognitions) {
        assertNotNull(recognition.getTitle());
        assertTrue(knownLabels.contains(recognition.getTitle()));
        assertFinite(recognition.getConfidence());
        final RectF location = recognition.getLocation();
        assertFinite(location.left);
        assertFinite(location.top);
        assertFinite(location.right);
        assertFinite(location.bottom);
      }

      detector.close();
      detector.close();
      expectThrows(IllegalStateException.class, () -> detector.recognizeImage(bitmap));
    } finally {
      detector.close();
      bitmap.recycle();
    }
  }

  @Test
  public void rejectsWrongBitmapDimensionsBeforeInference() throws Exception {
    final TFLiteObjectDetectionAPIModel detector = createDetector(targetContext().getAssets());
    final Bitmap wrongSize = Bitmap.createBitmap(INPUT_SIZE - 1, INPUT_SIZE, Bitmap.Config.ARGB_8888);
    try {
      expectThrows(IllegalArgumentException.class, () -> detector.recognizeImage(wrongSize));
    } finally {
      wrongSize.recycle();
      detector.close();
    }
  }

  @Test
  public void rejectsMalformedLabelsAndInputConfigurationBeforeNativeUse() throws Exception {
    final ByteBuffer model = loadDirectModel(targetContext().getAssets());
    final List<String> labels = validSyntheticLabels();

    final List<String> tooShort = new ArrayList<>(labels.subList(0, labels.size() - 1));
    expectThrows(
        IllegalArgumentException.class,
        () -> TFLiteObjectDetectionAPIModel.createForTesting(model, tooShort, INPUT_SIZE, true));

    final List<String> emptyLabel = new ArrayList<>(labels);
    emptyLabel.set(12, "   ");
    expectThrows(
        IllegalArgumentException.class,
        () -> TFLiteObjectDetectionAPIModel.createForTesting(model, emptyLabel, INPUT_SIZE, true));

    expectThrows(
        IllegalArgumentException.class,
        () -> TFLiteObjectDetectionAPIModel.createForTesting(model, labels, INPUT_SIZE - 1, true));
    expectThrows(
        IllegalArgumentException.class,
        () -> TFLiteObjectDetectionAPIModel.createForTesting(model, labels, INPUT_SIZE, false));
  }

  @Test
  public void rejectsTruncatedModelAndOutOfRangeClasses() throws Exception {
    final ByteBuffer truncated = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder());
    truncated.put(new byte[] {1, 2, 3, 4, 5, 6, 7, 8});
    truncated.rewind();
    expectThrows(
        RuntimeException.class,
        () ->
            TFLiteObjectDetectionAPIModel.createForTesting(
                truncated, validSyntheticLabels(), INPUT_SIZE, true));

    final TFLiteObjectDetectionAPIModel detector = createDetector(targetContext().getAssets());
    try {
      assertFalse(detector.labelForClass(0).isEmpty());
      expectThrows(IllegalStateException.class, () -> detector.labelForClass(90));
      expectThrows(IllegalStateException.class, () -> detector.labelForClass(-1));
      expectThrows(IllegalStateException.class, () -> detector.labelForClass(-2));
      expectThrows(IllegalStateException.class, () -> detector.labelForClass(1.5f));
      expectThrows(IllegalStateException.class, () -> detector.labelForClass(Float.NaN));
    } finally {
      detector.close();
    }
  }

  private static TFLiteObjectDetectionAPIModel createDetector(AssetManager assets) throws IOException {
    return (TFLiteObjectDetectionAPIModel)
        TFLiteObjectDetectionAPIModel.create(
            assets,
            "detect.tflite",
            "file:///android_asset/labelmap.txt",
            INPUT_SIZE,
            true);
  }

  private static Context targetContext() {
    return InstrumentationRegistry.getInstrumentation().getTargetContext();
  }

  private static List<String> readLabels(AssetManager assets) throws IOException {
    final String text = new String(readAll(assets.open("labelmap.txt")), "UTF-8");
    final String[] lines = text.split("\\r?\\n");
    final ArrayList<String> labels = new ArrayList<>();
    for (String line : lines) {
      if (!line.isEmpty()) {
        labels.add(line);
      }
    }
    return labels;
  }

  private static List<String> validSyntheticLabels() {
    final ArrayList<String> labels = new ArrayList<>();
    for (int index = 0; index < 91; index++) {
      labels.add("label-" + index);
    }
    return labels;
  }

  private static ByteBuffer loadDirectModel(AssetManager assets) throws IOException {
    final byte[] bytes = readAll(assets.open("detect.tflite"));
    final ByteBuffer buffer = ByteBuffer.allocateDirect(bytes.length).order(ByteOrder.nativeOrder());
    buffer.put(bytes);
    buffer.rewind();
    return buffer;
  }

  private static byte[] readAll(InputStream input) throws IOException {
    try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      final byte[] buffer = new byte[8192];
      int read;
      while ((read = stream.read(buffer)) != -1) {
        output.write(buffer, 0, read);
      }
      return output.toByteArray();
    }
  }

  private static String sha256(AssetManager assets, String path)
      throws IOException, NoSuchAlgorithmException {
    final MessageDigest digest = MessageDigest.getInstance("SHA-256");
    try (InputStream input = assets.open(path)) {
      final byte[] buffer = new byte[8192];
      int read;
      while ((read = input.read(buffer)) != -1) {
        digest.update(buffer, 0, read);
      }
    }
    final StringBuilder hex = new StringBuilder();
    for (byte value : digest.digest()) {
      hex.append(String.format("%02x", value & 0xff));
    }
    return hex.toString();
  }

  private static void assertFinite(float value) {
    assertFalse(Float.isNaN(value));
    assertFalse(Float.isInfinite(value));
  }

  private static <T extends Throwable> void expectThrows(
      Class<T> expected, ThrowingRunnable action) {
    try {
      action.run();
      fail("Expected " + expected.getSimpleName());
    } catch (Throwable error) {
      if (!expected.isInstance(error)) {
        throw new AssertionError(
            "Expected " + expected.getSimpleName() + " but got " + error.getClass().getSimpleName(),
            error);
      }
    }
  }

  private interface ThrowingRunnable {
    void run() throws Exception;
  }
}
