package neural_network.layers.layer_2d;

import jcuda.driver.JCudaDriver;
import lombok.Getter;
import lombok.SneakyThrows;
import neural_network.initialization.Initializer;
import neural_network.optimizers.Optimizer;
import neural_network.regularization.Regularization;
import nnarrays.NNArray;
import nnarrays.NNArrays;
import nnarrays.NNMatrix;
import nnarrays.NNVector;
import utilities.Use;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static utilities.JCudaHelper.CONTEXT;

public class DenseLayer2D extends NeuralLayer2D {
    //trainable parts
    private Regularization regularization;
    private Initializer initializer;
    private boolean loadWeight;

    protected final int countNeuron;

    //weightAttention and threshold
    @Getter
    private NNMatrix weight;
    private NNMatrix derWeight;

    private NNVector threshold;
    private NNVector derThreshold;

    public DenseLayer2D(int countNeuron) {
        super();
        this.countNeuron = countNeuron;
        this.trainable = true;
        initializer = new Initializer.HeNormal();
    }

    @Override
    public void initialize(Optimizer optimizer) {
        optimizer.addDataOptimize(weight, derWeight);
        optimizer.addDataOptimize(threshold, derThreshold);
    }

    public DenseLayer2D setTrainable(boolean trainable) {
        this.trainable = trainable;

        return this;
    }

    public DenseLayer2D setInitializer(Initializer initializer) {
        this.initializer = initializer;

        return this;
    }

    public DenseLayer2D setRegularization(Regularization regularization) {
        this.regularization = regularization;

        return this;
    }

    @Override
    public int info() {
        int countParam = weight.size() + threshold.size();
        System.out.println("Dense time\t| " + width + ",\t" + depth + "\t\t| " + outWidth + ",\t" + outDepth + "\t\t|\t" + countParam);
        return countParam;
    }

    @Override
    public void save(FileWriter writer) throws IOException {
        writer.write("Dense layer 2D\n");
        writer.write(countNeuron + "\n");
        threshold.save(writer);
        weight.save(writer);
        if (regularization != null) {
            regularization.write(writer);
        } else {
            writer.write("null\n");
        }
        writer.write(trainable + "\n");
        writer.flush();
    }

    @Override
    public void initialize(int[] size) {
        if (size.length != 2) {
            throw new ExceptionInInitializerError("Error size pre layer!");
        }
        depth = size[1];
        width = size[0];
        outWidth = width;
        outDepth = countNeuron;

        derThreshold = new NNVector(countNeuron);
        derWeight = new NNMatrix(depth, countNeuron);

        if (!loadWeight) {
            threshold = new NNVector(countNeuron);
            weight = new NNMatrix(depth, countNeuron);
            initializer.initialize(weight);
        }
    }

    @SneakyThrows
    @Override
    public void generateOutput(NNArray[] inputs) {
        this.input = NNArrays.isMatrix(inputs);
        this.output = new NNMatrix[input.length];

        ExecutorService executor = Executors.newFixedThreadPool(input.length);
        for (int t = 0; t < input.length; t++) {
            final int i = t;
            executor.execute(() -> {
                if (Use.GPU) {
                    JCudaDriver.cuCtxSetCurrent(CONTEXT);
                }

                output[i] = input[i].dot(weight);
                output[i].add(threshold);
            });
        }
        executor.shutdown();
        while (!executor.isTerminated()) {
        }
    }

    @SneakyThrows
    @Override
    public void generateError(NNArray[] errors) {
        errorNL = getErrorNextLayer(errors);
        this.error = new NNMatrix[errors.length];

        ExecutorService executor = Executors.newFixedThreadPool(input.length);
        for (int t = 0; t < input.length; t++) {
            final int i = t;
            executor.execute(() -> {
                if (Use.GPU) {
                    JCudaDriver.cuCtxSetCurrent(CONTEXT);
                }

                error[i] = errorNL[i].dotT(weight);
                if (trainable) {
                    derWeight.add(input[i].transpose().dot(errorNL[i]));
                    derThreshold.add(errorNL[i]);
                }
            });
        }
        executor.shutdown();
        while (!executor.isTerminated()) {
        }

        if (trainable && regularization != null) {
            regularization.regularization(weight);
            regularization.regularization(threshold);
        }
    }

    public static DenseLayer2D read(Scanner scanner) {
        DenseLayer2D denseLayer = new DenseLayer2D(Integer.parseInt(scanner.nextLine()));
        denseLayer.threshold = NNVector.read(scanner);
        denseLayer.weight = NNMatrix.read(scanner);
        denseLayer.setRegularization(Regularization.read(scanner));
        denseLayer.setTrainable(Boolean.parseBoolean(scanner.nextLine()));
        denseLayer.loadWeight = true;
        return denseLayer;
    }
}
