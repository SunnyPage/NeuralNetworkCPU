package neural_network.layers.layer_2d;

import jcuda.driver.JCudaDriver;
import neural_network.activation.FunctionActivation;
import nnarrays.NNArray;
import nnarrays.NNArrays;
import nnarrays.NNMatrix;
import utilities.Use;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static utilities.JCudaHelper.CONTEXT;

public class ActivationLayer2D extends NeuralLayer2D {
    private final FunctionActivation functionActivation;

    public ActivationLayer2D(FunctionActivation functionActivation) {
        this.functionActivation = functionActivation;
    }

    @Override
    public void generateOutput(NNArray[] input) {
        this.input = NNArrays.isMatrix(input);
        this.output = new NNMatrix[input.length];

        if (Use.CPU) {
            ExecutorService executor = Executors.newFixedThreadPool(input.length);
            for (int t = 0; t < input.length; t++) {
                final int i = t;
                executor.execute(() -> {
                    this.output[i] = new NNMatrix(width, depth);
                    functionActivation.activation(input[i], output[i]);
                });
            }
            executor.shutdown();
            while (!executor.isTerminated()) {
            }
        }

        if (Use.GPU) {
            for (int i = 0; i < input.length; i++) {
                this.output[i] = new NNMatrix(width, depth);
                functionActivation.activation(input[i], output[i]);
            }
        }
    }

    @Override
    public void generateError(NNArray[] error) {
        errorNL = getErrorNextLayer(error);
        this.error = new NNMatrix[errorNL.length];

        if (Use.CPU) {
            ExecutorService executor = Executors.newFixedThreadPool(input.length);
            for (int t = 0; t < input.length; t++) {
                final int i = t;
                executor.execute(() -> {
                    this.error[i] = new NNMatrix(width, depth);
                    functionActivation.derivativeActivation(input[i], output[i], errorNL[i], this.error[i]);
                });
            }
            executor.shutdown();
            while (!executor.isTerminated()) {
            }
        }

        if (Use.GPU) {
            for (int i = 0; i < input.length; i++) {
                this.error[i] = new NNMatrix(width, depth);
                functionActivation.derivativeActivation(input[i], output[i], errorNL[i], this.error[i]);
            }
        }
    }

    @Override
    public int info() {
        System.out.println("Activation\t|  " + width + ",\t" + depth + "\t\t| " + width + ",\t" + depth + "\t\t|");
        return 0;
    }

    @Override
    public void save(FileWriter writer) throws IOException {
        writer.write("Activation layer 2D\n");
        functionActivation.save(writer);
        writer.flush();
    }

    public static ActivationLayer2D read(Scanner scanner) {
        return new ActivationLayer2D(FunctionActivation.read(scanner));
    }
}