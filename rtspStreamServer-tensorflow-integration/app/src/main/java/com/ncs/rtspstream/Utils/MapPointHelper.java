package com.ncs.rtspstream.Utils;

import com.ubtechinc.cruzr.sdk.navigation.NavigationApi;
import com.ubtechinc.cruzr.sdk.navigation.model.MapPointModel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapPointHelper {
    public HashMap<String, String> mapNameIdHashMap;
    public HashMap<Integer, String> mapNameByLevelHashmap;

    public void initialiseMapID() {
        mapNameIdHashMap = new HashMap<>();
        mapNameIdHashMap.put("0653870a-4f78-4ec2-b555-300fe02002ff", "SIT7");
        mapNameIdHashMap.put("b19513e8-bc9b-4edb-bdf0-4416088d3dd4", "SIT6");
        mapNameIdHashMap.put("e8d7ca3b-2e2f-410d-bd2b-85758313cc03", "5ggarage");
        mapNameIdHashMap.put("58392799-5dac-44f3-8630-0c28ab81e111", "MoccaRoom");
        mapNameIdHashMap.put("54e4b970-ff54-4c17-a021-11b307d62d5e", "CodeX");
        //mapNameIdHashMap.put("3", "SIT1F");

        // map level to mapname
        mapNameByLevelHashmap = new HashMap<>();
        mapNameByLevelHashmap.put(7,"SIT7");
        mapNameByLevelHashmap.put(6,"SIT6");
        //mapNameByLevelHashmap.put(3,"MoccaRoom");
        //mapNameByLevelHashmap.put(3,"CodeX");
        mapNameByLevelHashmap.put(3,"5ggarage");

    }

    public String getMapVersionIdFromMapName(String currentMap)
    {
        for (Map.Entry<String, String> entry : mapNameIdHashMap.entrySet()) {
            if(entry.getValue().equals(currentMap))
            {
                return entry.getKey();
            }
        }
        return null;
    }

    public int getLevelByMapName(String currentMap)
    {
        for (Map.Entry<Integer, String> entry : mapNameByLevelHashmap.entrySet()) {
            if(entry.getValue().equals(currentMap))
            {
                return entry.getKey();
            }
        }
        return 0;
    }


    public float[] getMapPointPos(String rfmPosName, String curMapName) {
        List<MapPointModel> mapPointModels = NavigationApi.get().queryAllMapPointByMapName(curMapName);
        float pos[] = {0,0,0};
        for (MapPointModel point : mapPointModels) {
            if (point.getPointName().equals(rfmPosName)) {
                // TODO: May need to divide 100: Conversion from meters to centimeters
                // NCS NO NEED TO DIVIDE 100; SIT SIDE REQUIRES CONFIRMATION (NOT CHECKED)
                pos[0] = Float.parseFloat(point.getMapX());
                pos[1] = Float.parseFloat(point.getMapY());
                pos[2] = Float.parseFloat(point.getTheta());
                return pos;
            }
        }
        return pos;
    }
}
