package test.utilitas;

import data.mnist.MNISTLoader1D;
import neural_network.loss.FunctionLoss;
import neural_network.network.NeuralNetwork;
import neural_network.optimizers.*;
import trainer.DataMetric;
import trainer.DataTrainer;

import java.io.File;
import java.io.FileWriter;
import java.util.Scanner;

public class TestOptimizers {
    public static void main(String[] args) throws Exception {
//        NeuralNetwork network = new NeuralNetwork()
//                .addInputLayer(784)
//                .addLayer(new DenseLayer(64))
//                .addActivationLayer(new FunctionActivation.ReLU())
//                .addLayer(new DenseLayer(64))
//                .addActivationLayer(new FunctionActivation.ReLU())
//                .addLayer(new DenseLayer(10))
//                .addLayer(new ActivationLayer(new FunctionActivation.Softmax()))
//                .setOptimizer(new AdamOptimizer())
//                .setFunctionLoss(new FunctionLoss.MSE())
//                .create();
//
//        network.save(new FileWriter("optimizers_net.txt"));

        NeuralNetwork network = NeuralNetwork
                .read(new Scanner(new File("optimizers_net.txt")))
                .setOptimizer(new AngularGradOptimizer().useTan())
                .setFunctionLoss(new FunctionLoss.MSE())
                .create();

        MNISTLoader1D loader = new MNISTLoader1D();

        FileWriter writer = new FileWriter("optimizers_results.txt", true);

        DataTrainer trainer = new DataTrainer(60000, 10000, loader);
        network.info();

        String nameOptimizer = "AngularGrad";
        writer.write(nameOptimizer + "\t\t\t\t");

        for (int i = 0; i < 20; i++) {
            long start = System.nanoTime();
            writer.write(String.format("%.2f", trainer.train(network, 64, 1, new DataMetric.Top1())) + " / ");
            writer.write(String.format("%.2f", trainer.score(network, new DataMetric.Top1())) + "\t");
            System.out.println((System.nanoTime() - start) / 1000000);
            writer.flush();
        }
        writer.write("\n");
        writer.flush();
    }
}
