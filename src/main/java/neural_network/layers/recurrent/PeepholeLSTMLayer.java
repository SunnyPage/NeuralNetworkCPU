package neural_network.layers.recurrent;

import neural_network.activation.FunctionActivation;
import neural_network.initialization.Initializer;
import neural_network.optimizers.Optimizer;
import neural_network.regularization.Regularization;
import nnarrays.NNArray;
import nnarrays.NNArrays;
import nnarrays.NNMatrix;
import nnarrays.NNVector;
import utilities.CublasUtil;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PeepholeLSTMLayer extends RecurrentNeuralLayer {
    private NNVector[][] hiddenSMemory;
    private NNVector[][] hiddenLMemory;
    protected NNVector[][] inputHidden;
    protected NNVector[][] outputHidden;

    private NNVector[][] gateFInput;
    private NNVector[][] gateFOutput;
    private NNVector[][] gateIInput;
    private NNVector[][] gateIOutput;
    private NNVector[][] gateOInput;
    private NNVector[][] gateOOutput;
    private NNVector[][] gateCInput;
    private NNVector[][] gateCOutput;

    private NNMatrix[] weightInput;
    private NNMatrix[] derWeightInput;

    private NNMatrix[] weightHidden;
    private NNMatrix[] derWeightHidden;

    private NNMatrix[] weightPeephole;
    private NNMatrix[] derWeightPeephole;

    private NNVector[] threshold;
    private NNVector[] derThreshold;

    private final FunctionActivation functionActivationSigmoid;
    private final FunctionActivation functionActivationTanh;
    private FunctionActivation functionActivationOutput;

    private boolean hiddenPeephole;

    public PeepholeLSTMLayer(int countNeuron) {
        this(countNeuron, 0);
    }

    public PeepholeLSTMLayer(PeepholeLSTMLayer layer) {
        this(layer.countNeuron, layer.recurrentDropout, layer.returnSequences);
        this.copy(layer);
    }

    public PeepholeLSTMLayer(int countNeuron, double recurrentDropout) {
        super(countNeuron, recurrentDropout);

        this.functionActivationTanh = new FunctionActivation.Tanh();
        this.functionActivationOutput = new FunctionActivation.Tanh();
        this.functionActivationSigmoid = new FunctionActivation.Sigmoid();
        this.hiddenPeephole = false;
    }

    public PeepholeLSTMLayer setFunctionActivation(FunctionActivation functionActivation) {
        this.functionActivationOutput = functionActivation;

        return this;
    }

    public PeepholeLSTMLayer setHiddenPeephole(boolean hiddenPeephole) {
        this.hiddenPeephole = hiddenPeephole;

        return this;
    }

    public PeepholeLSTMLayer(int countNeuron, double recurrentDropout, boolean returnSequences) {
        this(countNeuron, recurrentDropout);
        setReturnSequences(returnSequences);
    }

    public PeepholeLSTMLayer setReturnSequences(boolean returnSequences) {
        this.returnSequences = returnSequences;

        return this;
    }

    @Override
    public void generateOutput(NNArray[] inputs, NNArray[][] state) {
        this.input = NNArrays.isMatrix(inputs);
        this.output = new NNMatrix[inputs.length];
        this.inputHidden = new NNVector[inputs.length][];
        this.outputHidden = new NNVector[inputs.length][];

        this.hiddenSMemory = new NNVector[inputs.length][];
        this.hiddenLMemory = new NNVector[inputs.length][];

        this.gateIInput = new NNVector[inputs.length][];
        this.gateIOutput = new NNVector[inputs.length][];
        this.gateFInput = new NNVector[inputs.length][];
        this.gateFOutput = new NNVector[inputs.length][];
        this.gateOInput = new NNVector[inputs.length][];
        this.gateOOutput = new NNVector[inputs.length][];
        this.gateCInput = new NNVector[inputs.length][];
        this.gateCOutput = new NNVector[inputs.length][];
        this.inputState = new NNVector[input.length][];
        this.state = new NNVector[input.length][2];

        ExecutorService executor = Executors.newFixedThreadPool(inputs.length);
        for (int cor = 0; cor < inputs.length; cor++) {
            final int i = cor;
            executor.execute(() -> {
                if (state != null) {
                    inputState[i] = NNArrays.isVector(state[i]);
                } else {
                    inputState[i] = null;
                }
                generateOutput(i, inputState[i]);
            });
        }
        executor.shutdown();
        while (!executor.isTerminated()) {
        }
    }

    @Override
    public void generateError(NNArray[] errors, NNArray[][] errorsState) {
        errorNL = getErrorNextLayer(errors);
        this.error = new NNMatrix[input.length];
        this.errorState = new NNVector[input.length][2];

        ExecutorService executor = Executors.newFixedThreadPool(errors.length);
        for (int cor = 0; cor < errors.length; cor++) {
            final int i = cor;
            executor.execute(() -> {
                if (errorsState != null) {
                    generateError(i, NNArrays.isVector(errorsState[i]));
                } else {
                    generateError(i, null);
                }
            });
        }
        executor.shutdown();
        while (!executor.isTerminated()) {
        }

        //regularization derivative weightAttention
        if (trainable && regularization != null) {
            for (int i = 0; i < 4; i++) {
                regularization.regularization(weightInput[i]);
                regularization.regularization(threshold[i]);
                if (hiddenPeephole) {
                    regularization.regularization(weightHidden[i]);
                }
                if (i < 3) {
                    regularization.regularization(weightPeephole[i]);
                }
            }
        }
    }

    @Override
    public void initialize(int[] size) {
        super.initialize(size);

        derThreshold = new NNVector[4];
        derWeightInput = new NNMatrix[4];
        derWeightPeephole = new NNMatrix[3];
        if (hiddenPeephole) {
            derWeightHidden = new NNMatrix[4];
        }

        for (int i = 0; i < 4; i++) {
            derThreshold[i] = new NNVector(countNeuron);
            derWeightInput[i] = new NNMatrix(countNeuron, depth);
            if (hiddenPeephole) {
                derWeightHidden[i] = new NNMatrix(countNeuron, countNeuron);
            }
            if (i < 3) {
                derWeightPeephole[i] = new NNMatrix(countNeuron, countNeuron);
            }
        }

        if (!loadWeight) {
            threshold = new NNVector[4];
            weightInput = new NNMatrix[4];
            if (hiddenPeephole) {
                weightHidden = new NNMatrix[4];
            }
            weightPeephole = new NNMatrix[3];

            for (int i = 0; i < 4; i++) {
                threshold[i] = new NNVector(countNeuron);
                weightInput[i] = new NNMatrix(countNeuron, depth);
                initializerInput.initialize(weightInput[i]);

                if (hiddenPeephole) {
                    weightHidden[i] = new NNMatrix(countNeuron, countNeuron);
                    initializerHidden.initialize(weightHidden[i]);
                }
                if (i < 3) {
                    weightPeephole[i] = new NNMatrix(countNeuron, countNeuron);
                    initializerHidden.initialize(weightPeephole[i]);
                }
            }
        }
    }

    @Override
    public void generateOutput(CublasUtil.Matrix[] input_gpu) {

    }

    @Override
    public void initialize(Optimizer optimizer) {
        for (int i = 0; i < 4; i++) {
            optimizer.addDataOptimize(weightInput[i], derWeightInput[i]);
            if (hiddenPeephole) {
                optimizer.addDataOptimize(weightHidden[i], derWeightHidden[i]);
            }
            if (i < 3) {
                optimizer.addDataOptimize(weightPeephole[i], derWeightPeephole[i]);
            }
            optimizer.addDataOptimize(threshold[i], derThreshold[i]);
        }
    }

    @Override
    public void generateError(CublasUtil.Matrix[] errors) {

    }

    @Override
    public int info() {
        int countParam = (weightInput[0].size() + threshold[0].size()) * 4 + weightPeephole[0].size() * 3;
        if (hiddenPeephole) {
            countParam += weightHidden[0].size() * 4;
        }
        System.out.println("PeepholeLSTM\t|  " + width + ",\t" + depth + "\t\t|  " + outWidth + ",\t" + countNeuron + "\t\t|\t" + countParam);
        return countParam;
    }

    @Override
    public void save(FileWriter writer) throws IOException {
        writer.write("Peephole LSTM layer\n");
        writer.write(countNeuron + "\n");
        writer.write(recurrentDropout + "\n");
        writer.write(returnSequences + "\n");
        writer.write(hiddenPeephole + "\n");

        for (int i = 0; i < 4; i++) {
            threshold[i].save(writer);
            weightInput[i].save(writer);
            if (hiddenPeephole) {
                weightHidden[i].save(writer);
            }
        }

        for (int i = 0; i < 3; i++) {
            weightPeephole[i].save(writer);
        }

        if (regularization != null) {
            regularization.write(writer);
        } else {
            writer.write("null\n");
        }
        writer.write(trainable + "\n");
        writer.flush();
    }

    private void generateOutput(int i, NNVector[] states) {
        int countRow = (returnSequences) ? input[i].getRow() : 1;
        output[i] = new NNMatrix(countRow, countNeuron);

        inputHidden[i] = new NNVector[input[i].getRow()];
        outputHidden[i] = new NNVector[input[i].getRow()];

        this.hiddenSMemory[i] = new NNVector[input[i].getRow()];
        this.hiddenLMemory[i] = new NNVector[input[i].getRow()];

        this.gateIInput[i] = new NNVector[input[i].getRow()];
        this.gateIOutput[i] = new NNVector[input[i].getRow()];
        this.gateFInput[i] = new NNVector[input[i].getRow()];
        this.gateFOutput[i] = new NNVector[input[i].getRow()];
        this.gateOInput[i] = new NNVector[input[i].getRow()];
        this.gateOOutput[i] = new NNVector[input[i].getRow()];
        this.gateCInput[i] = new NNVector[input[i].getRow()];
        this.gateCOutput[i] = new NNVector[input[i].getRow()];

        //pass through time
        for (int t = 0, tOut = 0; t < input[i].getRow(); t++) {
            inputHidden[i][t] = new NNVector(countNeuron);
            outputHidden[i][t] = new NNVector(countNeuron);

            this.hiddenSMemory[i][t] = new NNVector(countNeuron);
            this.hiddenLMemory[i][t] = new NNVector(countNeuron);

            this.gateIInput[i][t] = new NNVector(countNeuron);
            this.gateIOutput[i][t] = new NNVector(countNeuron);
            this.gateFInput[i][t] = new NNVector(countNeuron);
            this.gateFOutput[i][t] = new NNVector(countNeuron);
            this.gateOInput[i][t] = new NNVector(countNeuron);
            this.gateOOutput[i][t] = new NNVector(countNeuron);
            this.gateCInput[i][t] = new NNVector(countNeuron);
            this.gateCOutput[i][t] = new NNVector(countNeuron);

            NNVector hiddenS_t = null;
            NNVector hiddenL_t = null;
            if (t > 0) {
                hiddenS_t = hiddenSMemory[i][t - 1];
                hiddenL_t = inputHidden[i][t - 1];
            } else if (states != null) {
                hiddenS_t = states[0];
                hiddenL_t = states[1];
            }

            //generate new hiddenSMemory state for update and reset gate
            gateFInput[i][t].set(threshold[0]);
            gateIInput[i][t].set(threshold[1]);
            gateOInput[i][t].set(threshold[2]);
            gateCInput[i][t].set(threshold[3]);

            gateFInput[i][t].addMulRowToMatrix(input[i], t, weightInput[0]);
            gateIInput[i][t].addMulRowToMatrix(input[i], t, weightInput[1]);
            gateOInput[i][t].addMulRowToMatrix(input[i], t, weightInput[2]);
            gateCInput[i][t].addMulRowToMatrix(input[i], t, weightInput[3]);
            if (hiddenS_t != null && hiddenPeephole) {
                gateFInput[i][t].addMul(hiddenS_t, weightHidden[0]);
                gateIInput[i][t].addMul(hiddenS_t, weightHidden[1]);
                gateOInput[i][t].addMul(hiddenS_t, weightHidden[2]);
                gateCInput[i][t].addMul(hiddenS_t, weightHidden[3]);
            }
            if (hiddenL_t != null) {
                gateFInput[i][t].addMul(hiddenS_t, weightPeephole[0]);
                gateIInput[i][t].addMul(hiddenS_t, weightPeephole[1]);
                gateOInput[i][t].addMul(hiddenS_t, weightPeephole[2]);
            }

            //activation gate
            functionActivationSigmoid.activation(gateFInput[i][t], gateFOutput[i][t]);
            functionActivationSigmoid.activation(gateIInput[i][t], gateIOutput[i][t]);
            functionActivationSigmoid.activation(gateOInput[i][t], gateOOutput[i][t]);
            functionActivationTanh.activation(gateCInput[i][t], gateCOutput[i][t]);

            // find current long memory
            inputHidden[i][t].mulVectors(gateIOutput[i][t], gateCOutput[i][t]);
            if (hiddenL_t != null) {
                inputHidden[i][t].addProduct(hiddenL_t, gateFOutput[i][t]);
            }
            functionActivationOutput.activation(inputHidden[i][t], outputHidden[i][t]);

            hiddenSMemory[i][t].mulVectors(gateOOutput[i][t], outputHidden[i][t]);

            //dropout hiddenSMemory state
            if (dropout) {
                hiddenSMemory[i][t].dropout(hiddenSMemory[i][t], recurrentDropout);
            }
            //if return sequence pass current hiddenSMemory state to output
            if (returnSequences || t == input[i].getRow() - 1) {
                output[i].set(hiddenSMemory[i][t], tOut);
                tOut++;
            }
        }
        //if layer return state,than save last hiddenSMemory state
        state[i][0] = hiddenSMemory[i][input[i].getRow() - 1];
        state[i][1] = inputHidden[i][input[i].getRow() - 1];
    }

    private void generateError(int i, NNVector[] errorState) {
        this.error[i] = new NNMatrix(input[i]);
        NNVector hiddenError = new NNVector(countNeuron);
        NNVector hiddenLongDelta = new NNVector(countNeuron);
        NNVector hiddenLongError = new NNVector(countNeuron);

        NNVector gateFDelta = new NNVector(countNeuron);
        NNVector gateFError = new NNVector(countNeuron);
        NNVector gateIDelta = new NNVector(countNeuron);
        NNVector gateIError = new NNVector(countNeuron);
        NNVector gateODelta = new NNVector(countNeuron);
        NNVector gateOError = new NNVector(countNeuron);
        NNVector gateCDelta = new NNVector(countNeuron);
        NNVector gateCError = new NNVector(countNeuron);

        //copy error from next layer
        int tError = (returnSequences) ? hiddenSMemory[i].length - 1 : 0;
        if (errorNL != null) {
            hiddenError.setRowFromMatrix(errorNL[i], tError);
        }
        if (errorState != null) {
            hiddenError.add(errorState[0]);
            hiddenLongError.set(errorState[1]);
        }

        //pass through time
        for (int t = input[i].getRow() - 1; t >= 0; t--) {
            NNVector hiddenS_t = null;
            NNVector hiddenL_t = null;
            if (t > 0) {
                hiddenS_t = hiddenSMemory[i][t - 1];
                hiddenL_t = inputHidden[i][t - 1];
            } else if (inputState[i] != null) {
                hiddenS_t = inputState[i][0];
                hiddenL_t = inputState[i][1];
            }
            //dropout back for error
            hiddenError.dropoutBack(hiddenSMemory[i][t], hiddenError, recurrentDropout);
            //find error for long memory
            functionActivationTanh.derivativeActivation(inputHidden[i][t], outputHidden[i][t], hiddenError, hiddenLongDelta);
            hiddenLongDelta.mul(gateOOutput[i][t]);
            hiddenLongDelta.add(hiddenLongError);

            gateOError.mulVectors(hiddenError, outputHidden[i][t]);
            gateCError.mulVectors(hiddenLongDelta, gateIOutput[i][t]);
            gateIError.mulVectors(hiddenLongDelta, gateCOutput[i][t]);
            gateFDelta.clear();
            if (hiddenL_t != null) {
                gateFError.mulVectors(hiddenLongDelta, hiddenL_t);
                functionActivationSigmoid.derivativeActivation(gateFInput[i][t], gateFOutput[i][t], gateFError, gateFDelta);
            }

            functionActivationSigmoid.derivativeActivation(gateIInput[i][t], gateIOutput[i][t], gateIError, gateIDelta);
            functionActivationSigmoid.derivativeActivation(gateOInput[i][t], gateOOutput[i][t], gateOError, gateODelta);
            functionActivationTanh.derivativeActivation(gateCInput[i][t], gateCOutput[i][t], gateCError, gateCDelta);

            //find derivative for weightAttention
            if (trainable) {
                derivativeWeight(t, i, hiddenS_t, hiddenL_t, gateFDelta, gateIDelta, gateODelta, gateCDelta);
            }

            //find error for previous time step
            hiddenLongError.mulVectors(hiddenLongDelta, gateFOutput[i][t]);
            hiddenLongError.addMulT(gateFDelta, weightPeephole[0]);
            hiddenLongError.addMulT(gateIDelta, weightPeephole[0]);
            hiddenLongError.addMulT(gateCDelta, weightPeephole[0]);

            if (returnSequences && t > 0 && errorNL != null) {
                hiddenError.setRowFromMatrix(errorNL[i], t - 1);
            }
            if (hiddenPeephole) {
                hiddenError.addMulT(gateFDelta, weightHidden[0]);
                hiddenError.addMulT(gateIDelta, weightHidden[1]);
                hiddenError.addMulT(gateODelta, weightHidden[2]);
                hiddenError.addMulT(gateCDelta, weightHidden[3]);
            }

            //find error for previous layer
            error[i].addMulT(t, gateFDelta, weightInput[0]);
            error[i].addMulT(t, gateIDelta, weightInput[1]);
            error[i].addMulT(t, gateODelta, weightInput[2]);
            error[i].addMulT(t, gateCDelta, weightInput[3]);
        }

        this.errorState[i][0] = hiddenError;
        this.errorState[i][1] = hiddenLongError;
    }

    private void derivativeWeight(int t, int i, NNVector hiddenS_t, NNVector hiddenL_t,
                                  NNVector gateFDelta, NNVector gateIDelta, NNVector gateODelta, NNVector gateCDelta) {
        derThreshold[0].add(gateFDelta);
        derThreshold[1].add(gateIDelta);
        derThreshold[2].add(gateODelta);
        derThreshold[3].add(gateCDelta);
        int indexHWeight = 0, indexHWeightS = 0, indexIWeight = 0, indexInput;

        for (int k = 0; k < hiddenSMemory[i][t].size(); k++) {
            indexInput = input[i].getRowIndex()[t];
            indexHWeightS = indexHWeight;
            if (hiddenS_t != null && hiddenPeephole) {
                //find derivative for hiddenSMemory weightAttention
                for (int m = 0; m < countNeuron; m++, indexHWeight++) {
                    derWeightHidden[0].getData()[indexHWeight] += gateFDelta.get(k) * hiddenS_t.get(m);
                    derWeightHidden[1].getData()[indexHWeight] += gateIDelta.get(k) * hiddenS_t.get(m);
                    derWeightHidden[2].getData()[indexHWeight] += gateODelta.get(k) * hiddenS_t.get(m);
                    derWeightHidden[3].getData()[indexHWeight] += gateCDelta.get(k) * hiddenS_t.get(m);
                }
            }
            indexHWeight = indexHWeightS;
            if (hiddenL_t != null) {
                //find derivative for hidden long memory weightAttention
                for (int m = 0; m < countNeuron; m++, indexHWeight++) {
                    derWeightPeephole[0].getData()[indexHWeight] += gateFDelta.get(k) * hiddenL_t.get(m);
                    derWeightPeephole[1].getData()[indexHWeight] += gateIDelta.get(k) * hiddenL_t.get(m);
                    derWeightPeephole[2].getData()[indexHWeight] += gateODelta.get(k) * hiddenL_t.get(m);
                }
            }
            //find derivative for input's weightAttention
            for (int m = 0; m < input[i].getColumn(); m++, indexIWeight++, indexInput++) {
                derWeightInput[0].getData()[indexIWeight] += gateFDelta.get(k) * input[i].getData()[indexInput];
                derWeightInput[1].getData()[indexIWeight] += gateIDelta.get(k) * input[i].getData()[indexInput];
                derWeightInput[2].getData()[indexIWeight] += gateODelta.get(k) * input[i].getData()[indexInput];
                derWeightInput[3].getData()[indexIWeight] += gateCDelta.get(k) * input[i].getData()[indexInput];
            }
        }
    }

    public PeepholeLSTMLayer setRegularization(Regularization regularization) {
        this.regularization = regularization;

        return this;
    }

    public PeepholeLSTMLayer setInitializer(Initializer initializer) {
        this.initializerInput = initializer;
        this.initializerHidden = initializer;

        return this;
    }

    public PeepholeLSTMLayer setInitializerInput(Initializer initializer) {
        this.initializerInput = initializer;

        return this;
    }

    public PeepholeLSTMLayer setInitializerHidden(Initializer initializer) {
        this.initializerHidden = initializer;

        return this;
    }

    public PeepholeLSTMLayer setTrainable(boolean trainable) {
        this.trainable = trainable;

        return this;
    }

    public static PeepholeLSTMLayer read(Scanner scanner) {
        PeepholeLSTMLayer recurrentLayer = new PeepholeLSTMLayer(Integer.parseInt(scanner.nextLine()),
                Double.parseDouble(scanner.nextLine()),
                Boolean.parseBoolean(scanner.nextLine()));

        recurrentLayer.hiddenPeephole = Boolean.parseBoolean(scanner.nextLine());

        recurrentLayer.threshold = new NNVector[4];
        recurrentLayer.weightInput = new NNMatrix[4];
        recurrentLayer.weightHidden = new NNMatrix[4];

        for (int i = 0; i < 4; i++) {
            recurrentLayer.threshold[i] = NNVector.read(scanner);
            recurrentLayer.weightInput[i] = NNMatrix.read(scanner);
            if (recurrentLayer.hiddenPeephole) {
                recurrentLayer.weightHidden[i] = NNMatrix.read(scanner);
            }
        }

        for (int i = 0; i < 3; i++) {
            recurrentLayer.weightPeephole[i] = NNMatrix.read(scanner);
        }

        recurrentLayer.setRegularization(Regularization.read(scanner));
        recurrentLayer.setTrainable(Boolean.parseBoolean(scanner.nextLine()));
        recurrentLayer.loadWeight = true;
        return recurrentLayer;
    }
}