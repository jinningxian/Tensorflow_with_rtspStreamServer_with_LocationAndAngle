package com.ncs.rtspstream.Utils;

/**
 * This class draws the canvas and POI on the map
 */
public class MapUtils {

    // Draw Map with POI & 'You Are Here' Marker
/*    public Bitmap drawMap(Context context, int floorPlan, int level, int currentLevel, List<Poi> poiList) {
        Resources resources = context.getResources();
        Bitmap map = BitmapFactory.decodeResource(resources, floorPlan);

        Bitmap destinationMarker = BitmapFactory.decodeResource(resources, R.drawable.ic_map_marker);
        Bitmap currentLocationMarker = BitmapFactory.decodeResource(resources, R.drawable.ic_marker_blue);
        Bitmap urHereMarker = BitmapFactory.decodeResource(resources, R.drawable.ic_marker_you_are_here);
        Canvas canvas, canvas2;
        Paint paint;
        Bitmap bmp;
        Matrix matrix;

        int width, height;

        width = map.getWidth();
        height = map.getHeight();

        // Create the new bitmap
        bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);

        // Copy the original bitmap into the new one
        canvas = new Canvas(bmp);
        canvas2 = new Canvas(bmp);

        float scaleX = canvas.getWidth()/width;
        float scaleY = canvas.getHeight()/height;
        // Draw the floor plan
        canvas.drawBitmap(map, 0, 0, paint);

        for(int i=0;i<poiList.size();i++)
        {
            if (poiList.get(i).getLevel()==level) {
//            canvas.drawBitmap(destinationMarker, (poi.getX_pos()) + (destinationMarker.getWidth()/2 ), (poi.getY_pos()) + (destinationMarker.getHeight()/2 ), paint);
                //setMapTouchSensor(poi,canvas);

                // for sit map only the scale is 2 times
                //canvas.drawBitmap(destinationMarker, (poi.getX_pos() - destinationMarker.getWidth()/2)*2, (poi.getY_pos() - destinationMarker.getHeight()/2)*2, paint);
                //canvas.drawBitmap(destinationMarker, poi.getX_pos() *2, poi.getY_pos()*2, paint);
                canvas.drawBitmap(destinationMarker, poiList.get(i).getX_pos() *2 - destinationMarker.getWidth(), poiList.get(i).getY_pos()*2 - destinationMarker.getHeight(), paint);

            }
        }


        if (currentLevel == level) {
            float[] pos = app.convertMapPositionToDisplayMapScaled(currentLevel);
            //float[] pos = {755, 612, 0};
            canvas.drawBitmap(urHereMarker, (pos[0] * 2) - (urHereMarker.getWidth() / 2), (pos[1] * 2) - (currentLocationMarker.getHeight() / 2) - urHereMarker.getHeight(), paint);

            // Create the matrix
            matrix = new Matrix();

            // The values dx and dy specify the offset from the coordinates (0, 0),
            // where the next drawable will be drawn
            canvas2.translate((pos[0] * 2) - (currentLocationMarker.getWidth() / 2), (pos[1] * 2) - (currentLocationMarker.getHeight() / 2));
            canvas2.rotate(pos[2], 50, 50);
            canvas2.drawBitmap(currentLocationMarker, matrix, paint);
        }

        map.recycle();
        //destinationMarker.recycle();
        currentLocationMarker.recycle();
        urHereMarker.recycle();

        return bmp;
    }*/
}