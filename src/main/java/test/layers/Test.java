package test.layers;

import neural_network.layers.layer_1d.DenseLayer;
import neural_network.loss.FunctionLoss;
import neural_network.optimizers.AdamOptimizer;
import neural_network.optimizers.Optimizer;
import nnarrays.NNArrays;
import nnarrays.NNVector;

public class Test {
    public static void main(String[] args) {
        NNVector[] input = new NNVector[128];
        NNVector[] output = new NNVector[input.length];

        int inputSize = 256;
        int outputSize = 128;

        for (int i = 0; i < input.length; i++) {
            input[i] = new NNVector(inputSize);
            output[i] = new NNVector(outputSize);

            for (int j = 0; j < inputSize; j++) {
                input[i].set(j, (float) (Math.random() - 0.5f));
            }
            for (int j = 0; j < outputSize; j++) {
                output[i].set(j, (float) (Math.random() - 0.5f));
            }
        }

        Optimizer optimizer = new AdamOptimizer();

        DenseLayer layer = new DenseLayer(outputSize, false);
        layer.initialize(new int[]{inputSize});
        layer.initialize(optimizer);

        FunctionLoss loss = new FunctionLoss.MSE();

        for (int i = 0; i < 128; i++) {
            long start = System.nanoTime();
            layer.generateOutput(input);
            System.out.println(loss.findAccuracy(layer.getOutput(), output));
            layer.generateError(NNArrays.toVector(loss.findDerivative(layer.getOutput(), output)));
            optimizer.update();
            System.out.println((System.nanoTime() - start) / 1000000);
        }
    }
}
