package test.layers;

import neural_network.initialization.Initializer;
import neural_network.layers.recurrent.*;
import neural_network.loss.FunctionLoss;
import neural_network.optimizers.AdamOptimizer;
import neural_network.optimizers.Optimizer;
import nnarrays.NNArrays;
import nnarrays.NNMatrix;

public class TestRecurrent {
    public static void main(String[] args) {
        NNMatrix[] input = new NNMatrix[64];
        NNMatrix[] output = new NNMatrix[input.length];

        Initializer initializer = new Initializer.RandomUniform();

        for (int i = 0; i < input.length; i++) {
            input[i] = new NNMatrix(64, 64);
            initializer.initialize(input[i]);
            output[i] = new NNMatrix(64, 64);

            initializer.initialize(output[i]);
        }

        Bidirectional layer = new Bidirectional(new GRULayer(32, 0.1, true));
        layer.initialize(new int[]{64, 64});
        Optimizer optimizer = new AdamOptimizer();
        layer.initialize(optimizer);

        FunctionLoss loss = new FunctionLoss.MSE();
//
        for (int i = 0; i < 128; i++) {
            long start = System.nanoTime();
            layer.generateTrainOutput(input);
//            System.out.println(Arrays.toString(layer.getOutput()[0].getData()));
            System.out.println(loss.findAccuracy(layer.getOutput(), output));
            layer.generateError(NNArrays.toMatrix(loss.findDerivative(layer.getOutput(), output), 64, 64));
            optimizer.update();
            System.out.println((System.nanoTime() - start) / 1000000);
        }
    }
}
