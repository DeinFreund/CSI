/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.neural;

import com.springrts.ai.oo.AIFloat3;
import java.util.Map;
import java.util.TreeMap;
import org.neuroph.core.Connection;
import org.neuroph.core.Layer;
import org.neuroph.core.NeuralNetwork;
import org.neuroph.core.Neuron;
import org.neuroph.core.data.DataSet;
import org.neuroph.core.learning.error.MeanSquaredError;
import org.neuroph.nnet.learning.MomentumBackpropagation;
import org.neuroph.nnet.learning.MomentumBackpropagation.MomentumWeightTrainingData;
import zkcbai.Command;
import zkcbai.UnitDamagedListener;
import zkcbai.UnitDestroyedListener;
import zkcbai.UpdateListener;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;
import zkcbai.unitHandlers.units.tasks.Task;
import zkcbai.unitHandlers.units.tasks.TaskIssuer;

/**
 *
 * @author User
 */
public class NeuralHandler implements UpdateListener, TaskIssuer, UnitDamagedListener, UnitDestroyedListener {

    AIUnit unit;

    private final static Map<Integer, NeuralNetwork> unitNets = new TreeMap();
    private final static Map<Integer, MomentumBackpropagation> unitLearnings = new TreeMap();

    private static int lastSave;

    private NeuralNetwork net;
    private MomentumBackpropagation learning;
    private Command cmd;

    private final int w = 7, h = 7;
    private final float gamma = 0.8f;
    //The Gamma parameter has a range of 0 to 1.  If Gamma is closer to zero, the agent will tend to consider only immediate rewards.  
    //If Gamma is closer to one, the agent will consider future rewards with greater weight, willing to delay the reward.

    private float realW = 500, realH = 500;

    private double[] oldInput, oldOutput;
    private int oldDecision;

    private float reward = 0;

    private boolean isAlive = true;

    public NeuralHandler(AIUnit unit, Command cmd) {
        this.unit = unit;
        this.cmd = cmd;

        if (!unitNets.containsKey(unit.getDef().getUnitDefId())) {
            cmd.debug("Loading neural net..");
            boolean onErr = false;
            try{
                net = NeuralNetwork.createFromFile((unit.getDef().getName() + ".nnet"));
            }catch (Exception ex){
                cmd.debug("Loading failed: \n" , ex);
                onErr = true;
            }
            
            if (net.getInputsCount() != w * h * 2 || onErr) {
                cmd.debug("Creating neural net with " + w * h * 2 + " input nodes..");
                net = new SquareConvolutionNetwork(-w * h * 2, -100, -4);
            }

            cmd.debug("Initializing neural net..");
            learning = new MomentumBackpropagation();
            learning.setLearningRate(0.2);
            learning.setMomentum(0.25);
            learning.setErrorFunction(new MeanSquaredError(1));
            net.setLearningRule(learning);
            for (Layer layer : net.getLayers()) {
                for (Neuron neuron : layer.getNeurons()) {
                    for (Connection connection : neuron.getInputConnections()) {
                        if (connection.getWeight().getTrainingData() == null) {
                            connection.getWeight().setTrainingData(learning.new MomentumWeightTrainingData());
                        }
                    }
                }
            }
            unitNets.put(unit.getDef().getUnitDefId(), net);
            unitLearnings.put(unit.getDef().getUnitDefId(), learning);
        } else {
            net = unitNets.get(unit.getDef().getUnitDefId());
            learning = unitLearnings.get(unit.getDef().getUnitDefId());
        }

        cmd.debug("Neural net ready!");

        cmd.addSingleUpdateListener(this, cmd.getCurrentFrame() + 20);
    }

    @Override
    public void update(int frame) {

        if (!isAlive) {
            return;
        }

        if (frame - lastSave > 300) {
            lastSave = frame;
            net.save(unit.getDef().getName() + ".nnet");
        }

        float sx = unit.getPos().x - realW / 2;
        float sy = unit.getPos().z - realH / 2;

        float dx = realW / w;
        float dy = realH / h;

        float dpsGrid[][] = new float[w][h];
        float enemyGrid[][] = new float[w][h];

        for (Enemy e : cmd.getEnemyUnits(false)) {
            int x = (int) Math.round(e.getPos().x - sx / dx);
            int y = (int) Math.round(e.getPos().z - sy / dy);
            if (x >= 0 && x < w && y >= 0 && y < h) {
                enemyGrid[x][y] = 1;
            }
            for (int xx = (int) Math.round(Math.max(x - e.getMaxRange() / dx, 0)); xx < (int) Math.round(Math.min(x + e.getMaxRange() / dx, w)); xx++) {
                for (int yy = (int) Math.round(Math.max(y - e.getMaxRange() / dy, 0)); yy < (int) Math.round(Math.min(y + e.getMaxRange() / dx, h)); yy++) {
                    dpsGrid[xx][yy] += e.getDPS();
                }
            }
        }
        double[] inputVector = new double[w * h * 2];
        int i = 0;
        float maxDps = 120;
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                enemyGrid[x][y] *= 2;
                enemyGrid[x][y]--;
                inputVector[i] = enemyGrid[x][y];
                i++;
                //maxDps = Math.max(maxDps, dpsGrid[x][y]);
            }
        }
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {

                inputVector[i] = dpsGrid[x][y] / maxDps * 2 - 1;
                i++;
            }
        }
        net.setInput(inputVector);
        net.calculate();
        double sum = 0;
        for (double d : net.getOutput()) {
            sum += Math.max(0.05, d);
        }
        double rand = Math.random() * sum;

        int dir = 0;
        for (double d : net.getOutput()) {
            rand -= Math.max(0.05, d);
            if (rand <= 0) {
                break;
            }
            dir++;
        }
        AIFloat3 dpos = new AIFloat3();
        switch (dir) {
            case 0:
                dpos = new AIFloat3(0, 0, -1);
                break;
            case 1:
                dpos = new AIFloat3(1, 0, 0);
                break;
            case 2:
                dpos = new AIFloat3(0, 0, 1);
                break;
            case 3:
                dpos = new AIFloat3(-1, 0, 0);
                break;
        }
        dpos.scale(100);
        dpos.add(unit.getPos());
        unit.moveTo(dpos, Integer.MAX_VALUE);
        double[] tempInput = inputVector;
        double[] tempOutput = net.getOutput();

        if (oldInput != null) {
            DataSet dataSet = new DataSet(net.getInputsCount(), net.getOutputsCount());
            cmd.debug("Rewarding net with " + reward);
            net.setInput(inputVector);
            net.calculate();
            double maxQ = 0;
            for (Double d : net.getOutput()) {
                maxQ = Math.max(maxQ, d);
            }
            oldOutput[oldDecision] = reward + gamma * maxQ;
            dataSet.addRow(oldInput, oldOutput);
            learning.doOneLearningIteration(dataSet);
        }
        reward = 0;

        oldInput = tempInput;
        oldOutput = tempOutput;
        oldDecision = dir;
        cmd.addSingleUpdateListener(this, cmd.getCurrentFrame() + 20);
        cmd.addUnitDamagedListener(this);
        cmd.addUnitDestroyedListener(this);
    }

    @Override
    public void abortedTask(Task t) {

    }

    @Override
    public void finishedTask(Task t) {

    }

    @Override
    public void reportSpam() {
        throw new RuntimeException("I spammed MoveTasks!");
    }

    @Override
    public void unitDamaged(AIUnit u, Enemy killer, float damage) {
        if (u != null && u.equals(unit)) {
            reward -= damage / unit.getUnit().getMaxHealth() * unit.getMetalCost();
        }
    }

    @Override
    public void unitDamaged(Enemy e, AIUnit killer, float damage) {
        if (e != null && killer != null && killer.equals(unit)) {
            reward += damage / e.getDef().getHealth() * e.getMetalCost();
        }
    }

    @Override
    public void unitDestroyed(AIUnit u, Enemy killer) {
        if (u.equals(unit)) {
            isAlive = false;
        }
    }

    @Override
    public void unitDestroyed(Enemy e, AIUnit killer) {
    }
}
