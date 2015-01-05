package zkcbai.helpers;

import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.util.List;

import com.springrts.ai.oo.AIFloat3;
import com.springrts.ai.oo.clb.Map;
import com.springrts.ai.oo.clb.OOAICallback;
import zkcbai.Command;
import zkcbai.unitHandlers.units.AIUnit;

public class RadarManager extends Helper {

    Command command;

    private List<Integer> radarMap;
    private BufferedImage radarImage;

    private int mapWidth;
    private int mapHeight;
    private int radarResolution;
    private int gridWidth;
    private int gridHeight;
    private Map map;

    private int radarGridSize;

    public RadarManager(Command cmd, OOAICallback clbk) {
        super(cmd, clbk);
        this.map = clbk.getMap();
        this.mapHeight = map.getHeight();
        this.mapWidth = map.getWidth();
        this.radarResolution = 3;
        this.radarGridSize = (int) Math.pow((double) 2, (double) radarResolution);
        this.radarMap = map.getRadarMap();
        this.gridWidth = mapWidth / radarGridSize;
        this.gridHeight = mapHeight / radarGridSize;
        this.radarImage = new BufferedImage(gridWidth + 1, gridHeight + 1, BufferedImage.TYPE_BYTE_GRAY);

        this.updateRadarImage();

    }


    @Override
    public void update(int frame) {
        this.radarMap = map.getRadarMap();

        try {
            if (frame % 5 == 0) {
                updateRadarImage();
            }
        } catch (Exception e) {
            command.debug("Exception in RadarManager update: ",e);
        }

    }

    private void updateRadarImage() {
        if (radarImage != null) {
            WritableRaster r = (WritableRaster) radarImage.getData();
            for (int x = 0; x < gridWidth; x++) {
                for (int z = 0; z < gridHeight; z++) {
                    int coord = Math.min(x + z * gridWidth, radarMap.size() - 1);

                    int value = radarMap.get(coord);

                    float[] pixel = new float[1];
                    pixel[0] = value * 4;
                    try {
                        r.setPixel(x, z, pixel);
                    } catch (Exception e) {
                        command.debug("Exception when setting radarpixel <" + x + "," + z + "> out of <" + gridWidth + "x" + gridHeight + ">: ",e);
                    }
                }
            }
            radarImage.setData(r);
        } else {
            command.debug("radarImage is null!");
        }
    }

    public BufferedImage getImage() {
        return this.radarImage;
    }

    public boolean isInRadar(AIFloat3 position) {
        return isInRadar(position, 0);
    }

    public boolean isInRadar(AIFloat3 position, int level) {
		//the value for the full resolution position (x, z) is at index ((z * width + x) / res) -
        //the last value, bottom right, is at index (width/res * height/res - 1)

        // convert from world coordinates to heightmap coordinates
        double x = (int) Math.floor(position.x / 8);
        double z = (int) Math.floor(position.z / 8);

        int gridX = (int) Math.floor((x / mapWidth) * gridWidth);
        int gridZ = (int) Math.floor((z / mapHeight) * gridHeight);

        int index = gridX + gridZ * gridWidth;

        /*if (index > radarMap.size()) { // exception helps debug
            return false;
        }*/
        return (radarMap.get(index) > level);
    }

    @Override
    public void unitFinished(AIUnit u) {
        
    }

}
