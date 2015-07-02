/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.neural;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.neuroph.core.Connection;
import org.neuroph.core.Layer;
import org.neuroph.core.Neuron;
import org.neuroph.core.Weight;
import org.neuroph.core.input.WeightedSum;
import org.neuroph.core.transfer.Linear;
import org.neuroph.nnet.MultiLayerPerceptron;
import org.neuroph.nnet.comp.neuron.BiasNeuron;
import org.neuroph.nnet.comp.neuron.InputNeuron;
import org.neuroph.nnet.learning.MomentumBackpropagation;
import org.neuroph.util.ConnectionFactory;
import org.neuroph.util.LayerFactory;
import org.neuroph.util.NeuralNetworkFactory;
import org.neuroph.util.NeuralNetworkType;
import org.neuroph.util.NeuronProperties;
import org.neuroph.util.NeurophArrayList;
import org.neuroph.util.TransferFunctionType;
import org.neuroph.util.random.NguyenWidrowRandomizer;

/**
 *
 * @author User
 */
public class SquareConvolutionNetwork extends MultiLayerPerceptron {

    private int sqrt(int num) {
        return (int) Math.round(Math.sqrt(num));
    }

    /**
     *
     * @param layers size of each layer, negative numbers indicate Perceptron
     * layers
     */
    public SquareConvolutionNetwork(int... layers) {
        super(1);

        while (getLayersCount() > 1) {
            this.removeLayerAt(1);
        }

        try {
            Field inputNeurons = this.getClass().getSuperclass().getSuperclass().getDeclaredField("inputNeurons");
            inputNeurons.setAccessible(true);
            inputNeurons.set(this, new NeurophArrayList<>(Neuron.class));
            Field slayers = getClass().getSuperclass().getSuperclass().getDeclaredField("layers");
            slayers.setAccessible(true);
            slayers.set(this, new NeurophArrayList<>(Layer.class));
            Field outputNeurons = getClass().getSuperclass().getSuperclass().getDeclaredField("outputNeurons");
            outputNeurons.setAccessible(true);
            outputNeurons.set(this, new NeurophArrayList<>(Neuron.class));
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        NeuronProperties neuronProperties = new NeuronProperties();
        neuronProperties.setProperty("useBias", false);
        neuronProperties.setProperty("transferFunction", SteepSigmoid.class);
        neuronProperties.setProperty("inputFunction", WeightedSum.class);

        List<Integer> neuronsInLayersVector = new ArrayList<>();
        List<Boolean> convolutionLayers = new ArrayList<>();
        for (int i = 0; i < layers.length; i++) {
            if (layers[i] > 0) {
                if (Math.abs(Math.sqrt(layers[i]) - Math.round(Math.sqrt(layers[i]))) > 0.00001) {
                    throw new RuntimeException("Input not square");
                }
                neuronsInLayersVector.add(layers[i]);
                convolutionLayers.add(true);
            } else {
                neuronsInLayersVector.add(-layers[i]);
                convolutionLayers.add(false);
            }
        }

        this.createNetwork(neuronsInLayersVector, convolutionLayers, neuronProperties);
    }

    private int getX(int i, int layerSize) {
        return i % sqrt(layerSize);
    }

    private int getY(int i, int layerSize) {
        return i / sqrt(layerSize);
    }

    private int getI(int x, int y, int layerSize) {
        return x + y * sqrt(layerSize);
    }

    private void createNetwork(List<Integer> neuronsInLayers, List<Boolean> convolutionLayers, NeuronProperties neuronProperties) {

        // set network type
        this.setNetworkType(NeuralNetworkType.MULTI_LAYER_PERCEPTRON);

        // create input layer
        NeuronProperties inputNeuronProperties = new NeuronProperties(InputNeuron.class, Linear.class);
        Layer layer = LayerFactory.createLayer(neuronsInLayers.get(0), inputNeuronProperties);

        boolean useBias = true; // use bias neurons by default
        if (neuronProperties.hasProperty("useBias")) {
            useBias = (Boolean) neuronProperties.getProperty("useBias");
        }

        if (useBias) {
            layer.addNeuron(new BiasNeuron());
        }

        this.addLayer(layer);

        // create layers
        Layer prevLayer = layer;

        //for(Integer neuronsNum : neuronsInLayers)
        for (int layerIdx = 1; layerIdx < neuronsInLayers.size(); layerIdx++) {
            Integer neuronsNum = neuronsInLayers.get(layerIdx);
            // createLayer layer
            layer = LayerFactory.createLayer(neuronsNum, neuronProperties);

            // add created layer to network
            this.addLayer(layer);
            // createLayer full connectivity between previous and this layer
            if (prevLayer != null) {
                //ConnectionFactory.fullConnect(prevLayer, layer);
                if (convolutionLayers.get(layerIdx)) {
                    int embossSize = (sqrt(prevLayer.getNeuronsCount()) - sqrt(layer.getNeuronsCount()));
                    System.out.println(embossSize + 1);
                    Weight[] weights = new Weight[(embossSize + 1) * (embossSize + 1)];
                    for (int i = 0; i < weights.length; i++) {
                        weights[i] = new MultiuserWeight(layer.getNeuronsCount());
                    }
                    for (int i = 0; i < layer.getNeuronsCount(); i++) {
                        for (int x = getX(i, layer.getNeuronsCount()); x <= getX(i, layer.getNeuronsCount()) + embossSize; x++) {
                            for (int y = getY(i, layer.getNeuronsCount()); y <= getY(i, layer.getNeuronsCount()) + embossSize; y++) {

                                ConnectionFactory.createConnection(prevLayer.getNeuronAt(getI(x, y, prevLayer.getNeuronsCount())), layer.getNeuronAt(i));
                            }
                        }
                        for (int ii = 0; ii < layer.getNeuronAt(i).getInputConnections().length; ii++) {
                            Connection conn = layer.getNeuronAt(i).getInputConnections()[ii];
                            conn.setWeight(weights[ii]);
                        }

                    }
                    Weight biasWeight = new MultiuserWeight(layer.getNeuronsCount());
                    ConnectionFactory.createConnection(prevLayer.getNeuronAt(prevLayer.getNeuronsCount() - 1), layer);
                    for (Connection conn : prevLayer.getNeuronAt(prevLayer.getNeuronsCount() - 1).getOutConnections()) {
                        conn.setWeight(biasWeight);
                    }
                } else {
                    ConnectionFactory.fullConnect(prevLayer,layer);
                }
            }

            if (useBias && (layerIdx < (neuronsInLayers.size() - 1))) {
                layer.addNeuron(new BiasNeuron());
            }

            prevLayer = layer;
        }

        // set input and output cells for network
        NeuralNetworkFactory.setDefaultIO(this);

        // set learnng rule
        //this.setLearningRule(new BackPropagation(this));
        this.setLearningRule(new MomentumBackpropagation());
        // this.setLearningRule(new DynamicBackPropagation());

        this.randomizeWeights(new NguyenWidrowRandomizer(-0.7, 0.7));

    }
}
