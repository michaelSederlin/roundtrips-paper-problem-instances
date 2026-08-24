package disaggregate.kernels;

/**
 * Marker interface for all schedule-based kernels.
 * Allows ScheduleKernel, ScheduleOverlapKernel, and CombinedScheduleKernel
 * to all be found via getKernel(ScheduleKernelBase.class).
 */
public interface ScheduleKernelBase {
}
