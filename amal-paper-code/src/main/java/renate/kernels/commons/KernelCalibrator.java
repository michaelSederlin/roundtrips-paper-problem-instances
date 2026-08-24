package renate.kernels.commons;
// package renate.kernels;

// import org.apache.commons.lang3.NotImplementedException;
// import org.apache.commons.math3.optim.*;
// import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
// import org.apache.commons.math3.optim.nonlinear.scalar.MultivariateOptimizer;
// import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
// import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.BOBYQAOptimizer;
// import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.NelderMeadSimplex;
// import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.SimplexOptimizer;

// import java.io.*;
// import java.util.*;
// import java.util.function.Function;

// public class KernelCalibrator<D, K extends Kernel<D>> {
//     private final List<K> kernels;
//     private final List<D> data;
//     private final int n;
//     private final double[] qLevels;
//     private final double[] qTargets;
//     private OptimizeResult res;
//     public ALGORITHM algo;
//     public enum ALGORITHM {NELDER_MEAD, BOBYQA}
//     private String logFile;
//     private List<Double> lossHistory;
//     private List<double[]> paramHistory;
//     private List<double[]> kernelValueHistory;
//     private int iter;

//     public KernelCalibrator(List<D> data, double[] qLevels, double[] qTargets, Function<D, K> factory) {
//         Objects.requireNonNull(data, "data");
//         Objects.requireNonNull(qLevels, "qLevels");
//         Objects.requireNonNull(qTargets, "qTargets");

//         if (qLevels.length != qTargets.length)
//             throw new IllegalArgumentException("qLevels and qTargets must have same length");

//         this.data = List.copyOf(data);
//         this.n = data.size();
//         this.qLevels = Arrays.copyOf(qLevels, qLevels.length);
//         this.qTargets = Arrays.copyOf(qTargets,  qTargets.length);

//         List<K> ks = new ArrayList<K>(data.size());
//         for (D d : data) {
//             ks.add(factory.apply(d));
//         }
//         this.kernels = Collections.unmodifiableList(ks);
//         this.algo = ALGORITHM.NELDER_MEAD;
//     }
//     public List<K> kernels() {return kernels;}
//     public List<D> data() {return data;}
//     public double[] qLevels() {return qLevels;}
//     public double[] qTargets() {return qTargets;}

//     public class SolutionCallback implements OptimizationData {

//     }

//     public static final class OptimizeResult {
//         public final double[] x;
//         public final double fun;
//         public final int nfev;
//         public final int nit;
//         public final boolean success;
//         public final String message;
//         OptimizeResult(double[] x, double fun, int nfev, int nit, boolean success, String message) {
//             this.x = x;
//             this.fun = fun;
//             this.nfev = nfev;
//             this.nit = nit;
//             this.success = success;
//             this.message = message;
//         }
//         public String toString() {
//             return "Optimizer Results: " +
//                     "\n\tPoint: " + Arrays.toString(this.x) +
//                     "\n\tFunction value: " + this.fun +
//                     "\n\tNumber of function evaluations: " + this.nfev +
//                     "\n\tNumber of iterations: " + this.nit +
//                     "\n\tSuccess: " + this.success +
//                     "\n\tMessage: " + this.message;
//         }
//     }

//     public OptimizeResult minimize(double[] x0) {
//         switch(algo) {
//             case BOBYQA: {
//                 int ip = Math.max(2 * x0.length + 1, x0.length + 2);
//                 BOBYQAOptimizer opt = new BOBYQAOptimizer(ip);
//                 var bounds = new SimpleBounds(
//                     new double[]{0,0,0},
//                     new double[]{500, 999999, 10}
//                 );
// //                this.res = opt.optimize(
// //                        new SimpleBounds(
// //                                new double[]{0, 0, 0},
// //                                new double[]{200, 9999999, 10}
// //                                )
// //
// //                );
// //                return this.res;
//                 throw new NotImplementedException("BOBYQA IS NOT IMPLEMENTED YET!");
//             }
//             case NELDER_MEAD: {
//                 SimplexOptimizer opt = new SimplexOptimizer(1e-8, 1e-8);
//                 NelderMeadSimplex simplex = new NelderMeadSimplex(x0.length);
//                 x0 = Arrays.stream(x0).map(Math::log).toArray();
//                 this.res = doMinimize(opt,
//                         new MaxEval(300),
//                         new ObjectiveFunction(theta -> lossExpTransform(theta)),
// //                        new ObjectiveFunction(theta -> quantileDifferenceLoss(theta)),
//                         GoalType.MINIMIZE,
//                         new InitialGuess(x0),
//                         simplex
//                     );

//                 return this.res;
//             }
//             default:
//                 throw new IllegalStateException("INCORRECT ALGO " + algo);
//         }
//     }

//     private OptimizeResult doMinimize(MultivariateOptimizer opt, OptimizationData... optimArgs) {
//         this.lossHistory = new ArrayList<>();
//         this.paramHistory = new ArrayList<>();
//         this.kernelValueHistory = new ArrayList<>();
//         this.iter = 0;

//         PointValuePair res = opt.optimize(optimArgs);
//         double[] point = Arrays.stream(res.getPoint()).map(Math::exp).toArray();
//         if (logFile != null) {
//             try (PrintWriter out = new PrintWriter(logFile)){
//                 out.println("iteration\tloss\tparams\tkvals");
//                 for (int ii = 0; ii < this.iter; ii++) {
//                     out.println(ii + "\t" +
//                             lossHistory.get(ii) + "\t" +
//                             Arrays.toString(paramHistory.get(ii)) + "\t" +
//                             Arrays.toString(kernelValueHistory.get(ii))
//                     );

//                 }
//             } catch (FileNotFoundException e) {
//                 throw new RuntimeException(e);
//             }

//         }

//         return new OptimizeResult(point, res.getValue(), opt.getEvaluations(), opt.getIterations(), true, "Nelder-Mead");
//     }
//     public KernelCalibrator withLogging(String path) {
//         this.logFile = path;
//         return this;
//     }

//     public OptimizeResult res() {return this.res;}

//     private double lossExpTransform(double[] theta) {
//         double[] x = Arrays.stream(theta).map(Math::exp).toArray();
//         return quantileDifferenceLoss(x);
//     }

//     private double quantileDifferenceLoss(double[] x) {
//         for (K k: kernels) k.setParameters(x);
//         double[] kernelValues = new double[n * (n-1) / 2];
//         int k = 0;
//         for (int ii = 0; ii < n; ii++) {
//             K ki = kernels.get(ii);
//             for (int jj = ii + 1; jj < n; jj++) {
//                 kernelValues[k++] = ki.compute(data.get(jj));
//             }
//         }
//         Arrays.sort(kernelValues);

//         double qLoss = 0.0;
//         for (int ii = 0; ii < qTargets.length; ii++) {
//             double qEmp = percentile(kernelValues, qLevels[ii]);
//             double diff = qTargets[ii] - qEmp;
//             qLoss += diff * diff;
//         }

//         qLoss /= qTargets.length;

//         this.lossHistory.add(qLoss);
//         this.paramHistory.add(x);
//         this.kernelValueHistory.add(kernelValues);

//         System.out.println("Iteration: " + iter + "\tLoss: " + qLoss + "\tParameters: " + Arrays.toString(x));

//         iter++;
//         return qLoss;
//     }

//     public static double percentile(double[] vals, double p) {
//         if (vals.length == 0) return 0.0;
//         if (p <= 0) return vals[0];
//         if (p>= 1) return vals[vals.length - 1];
//         double r = p * (vals.length - 1);
//         int lo = (int) Math.floor(r);
//         int hi = (int) Math.ceil(r);
//         double w = r - lo;
//         return vals[lo] * (1 - w) + vals[hi] * w;
//     }

// }
