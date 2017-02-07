package zkcbai.helpers;

import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.util.List;

import com.springrts.ai.oo.AIFloat3;
import com.springrts.ai.oo.clb.Map;
import com.springrts.ai.oo.clb.OOAICallback;
import zkcbai.Command;
import zkcbai.unitHandlers.units.AIUnit;

public class LosManager extends Helper {


    private List<Integer> losMap;
    private BufferedImage losImage;

    private int mapWidth;
    private int mapHeight;
    private int losResolution;
    private int gridWidth;
    private int gridHeight;
    private Map map;

    private int losGridSize;

    public LosManager(Command cmd, OOAICallback clbk) {
        super(cmd, clbk);
        this.map = clbk.getMap();
        this.mapHeight = map.getHeight();
        this.mapWidth = map.getWidth();
        this.losResolution = clbk.getMod().getLosMipLevel();
        this.losGridSize = (int) Math.pow((double) 2, (double) losResolution);
        this.losMap = map.getLosMap();
        this.gridWidth = mapWidth / losGridSize;
        this.gridHeight = mapHeight / losGridSize;
        this.losImage = new BufferedImage(gridWidth + 1, gridHeight + 1, BufferedImage.TYPE_BYTE_GRAY);

        this.updateLosImage();

    }


    @Override
    public void update(int frame) {

        try {
            if (frame % (8 + frame / 5000) == 3 && command.getCommandDelay() < 60) {
                this.losMap = map.getLosMap();
                updateLosImage();
            }
        } catch (Exception e) {
            command.debug("Exception in LosManager update: ",e);
        }

    }

    private void updateLosImage() {
        if (losImage != null) {
            WritableRaster r = (WritableRaster) losImage.getData();
            for (int x = 0; x < gridWidth; x++) {
                for (int z = 0; z < gridHeight; z++) {
                    int coord = Math.min(x + z * gridWidth, losMap.size() - 1);

                    int value = losMap.get(coord);

                    float[] pixel = new float[1];
                    pixel[0] = value * 4;
                    try {
                        r.setPixel(x, z, pixel);
                    } catch (Exception e) {
                        command.debug("Exception when setting lospixel <" + x + "," + z + "> out of <" + gridWidth + "x" + gridHeight + ">: ",e);
                    }
                }
            }
            losImage.setData(r);
        } else {
            command.debug("losImage is null!");
        }
    }

    public BufferedImage getImage() {
        return this.losImage;
    }

    public boolean isInLos(AIFloat3 position) {
        return isInLos(position, 0);
    }

    public boolean isInLos(AIFloat3 position, int level) {
		//the value for the full resolution position (x, z) is at index ((z * width + x) / res) -
        //the last value, bottom right, is at index (width/res * height/res - 1)

        // convert from world coordinates to heightmap coordinates
        double x = (int) Math.floor(position.x / 8);
        double z = (int) Math.floor(position.z / 8);

        int gridX = (int) Math.floor((x / mapWidth) * gridWidth);
        int gridZ = (int) Math.floor((z / mapHeight) * gridHeight);

        int index =gridX + gridZ * gridWidth;

        if (index >= losMap.size() || index < 0) { // exception helps debug
            return false;
        }
        return (losMap.get(index) > level);
    }

    @Override
    public void unitFinished(AIUnit u) {
        
    }

}
