package com.ncs.rtspstream.Utils;

import com.ncs.rtspstream.Models.PositionName;
import com.ubtechinc.cruzr.sdk.navigation.NavigationApi;
import com.ubtechinc.cruzr.sdk.navigation.model.MapPointModel;

import java.util.HashMap;
import java.util.List;

public class MapPointHashMap {

    public static HashMap<String, String> mapPointHM;

    public static void initializeHashMap() {
        mapPointHM = new HashMap<>();
        // SIT - Testing in Lv. 7
//        mapPointHM.put("Lv1Lift_Lobby", "POINT B LOBBY");
//        mapPointHM.put("Lv1Lift", "POINT C LIFT");
//        mapPointHM.put("Lv3Lift", "POINT C LIFT");
//        mapPointHM.put("Lv3Lift_Lobby", "POINT B LOBBY");
//        mapPointHM.put("Lv3Destination", "BASE");
        // NCS - Testing in Mocha
        mapPointHM.put("Lv1Lift_Lobby", "two");
        mapPointHM.put("Lv1Lift", "InsideLiftA");
        mapPointHM.put("Lv3Lift", "InsideLiftB");
        mapPointHM.put("Lv3Lift_Lobby", "toilet");
        mapPointHM.put("Lv3Destination", "LvThreeDest");

    }

    public static void translateMapPointPos(String rfmPosName, PositionName pn) {
        pn.setAppPosName(mapPointHM.get(rfmPosName));
    }

    public static void setMapPointPos(String rfmPosName, PositionName pn, String curMapName) {
        pn.setAppPosName(mapPointHM.get(rfmPosName));
        List<MapPointModel> mapPointModels = NavigationApi.get().queryAllMapPointByMapName(curMapName);
        for (MapPointModel point : mapPointModels) {
            if (point.getPointName().equals(pn.getAppPosName())) {
                // TODO: May need to divide 100: Conversion from meters to centimeters
                // NCS NO NEED TO DIVIDE 100; SIT SIDE REQUIRES CONFIRMATION (NOT CHECKED)
                pn.setPosX(Double.parseDouble(point.getMapX()));
                pn.setPosY(Double.parseDouble(point.getMapY()));
                pn.setHeading(Double.parseDouble(point.getTheta()));
            }
        }
    }
}