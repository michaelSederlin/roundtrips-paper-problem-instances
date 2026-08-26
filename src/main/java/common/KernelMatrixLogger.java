package common;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.nio.file.Path;

import disaggregate.model.ActivityNode;
import disaggregate.weights.ResponseWeight;
import se.vti.utils.misc.metropolishastings.MHAbstractStateProcessor;
import se.vti.roundtrips.multiple.MultiRoundTrip;
import se.vti.utils.misc.metropolishastings.MHWeight;

/*
 * Logger that tracks kernel weights from survey responses for each person and round trip.
 * Logs traveler kernel weights, schedule kernel weights, and composite kernel weights.
 */
public class KernelMatrixLogger<N extends ActivityNode> extends MHAbstractStateProcessor<MultiRoundTrip<N>> {
//    private final List<SurveyResponse<N>> responses;
//    private final List<Person> population;
    private final ResponseWeight kernelWeights;
    private final File dir;

    public KernelMatrixLogger(long samplingInterval, Path dir, ResponseWeight kernelWeights) {
        super(0, samplingInterval);

        assert dir != null;
        this.dir =  dir.toFile();
        this.kernelWeights = kernelWeights;
    }

    @Override
    public void start() {

        // Create directory for matrix files
        if (!dir.exists()) {
            boolean result = dir.mkdirs();
        }

        double[][] personWeights = kernelWeights.getPersonResponseWeights();
        if (personWeights != null) {
            writeMatrix(
                    personWeights,
                    dir.toPath().resolve("personResponseWeights.tsv").toString()
            );
        }
    }

    @Override
    public void end() {
    }

    @Override
    public void processStateHook(MultiRoundTrip<N> state, double lw) {
        long ii = this.iteration();
        double[][] scheduleWeights = kernelWeights.getLastScheduleKernels();
        double[][] locationWeights = kernelWeights.getLastLocationKernels();

        if (scheduleWeights != null) writeMatrix(
                scheduleWeights,
                dir.toPath().resolve("scheduleWeights" + ii + ".tsv").toString()
        );
        if (locationWeights != null) writeMatrix(
                locationWeights,
                dir.toPath().resolve("locationWeights" + ii + ".tsv").toString()
        );
    }

    public static void writeMatrix(double[][] matrix, String filePath) {
        try (PrintWriter writer = new PrintWriter(filePath)) {
            if (matrix != null && matrix.length > 0 && matrix[0].length > 0) {
                int n = matrix.length;
                int m = matrix[0].length;
                for (int ii  = 0; ii < n; ii++) {
                    for (int jj = 0; jj < m; jj++) {
                        if (jj > 0) writer.print("\t");
                        writer.print(matrix[ii][jj]);
                    }
                    writer.println();
                }
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

    }
}
