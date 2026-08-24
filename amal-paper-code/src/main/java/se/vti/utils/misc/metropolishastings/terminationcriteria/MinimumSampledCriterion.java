/**
  * se.vti.utils.misc.metropolishastings.terminationcriteria
  *
  * Copyright (C) 2026 by Michael Sederlin (VTI, LiU).
  * Copyright (C) 2026 by Gunnar Flötteröd (VTI, LiU).
  *
  * VTI = Swedish National Road and Transport Institute
  * LiU = Linköping University, Sweden
  *
  * This program is free software: you can redistribute it and/or modify it under the terms
  * of the GNU General Public License as published by the Free Software Foundation, either
  * version 3 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU General Public License for more details.
  *
  * You should have received a copy of the GNU General Public License along with this program.
  * If not, see <https://www.gnu.org/licenses/>. See also COPYING and WARRANTY file.
  */
package se.vti.utils.misc.metropolishastings.terminationcriteria;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Decorates a stationarity-detecting {@link TerminationCriterion} (e.g.
 * {@link BlockAverageTerminationCriterion}) and delays termination until a
 * minimum number of samples, spaced sampleInterval iterations apart, have been
 * drawn from the stationary distribution.
 *
 * The wrapped delegate must NOT also be registered with the sampler; this
 * class forwards all state-processor calls to it and polls its terminate().
 * The first time the delegate reports termination is latched as the
 * stationarity iteration; this criterion then terminates once
 * minSamples * sampleInterval further iterations have elapsed. The latch never
 * resets, so a delegate that later flips back to non-stationary (as
 * BlockAverageTerminationCriterion can) does not restart the countdown; a
 * warning is logged if stationarity was lost by the time this criterion fires.
 *
 * @author MichaelS
 */
public class MinimumSampledCriterion<X> implements TerminationCriterion<X> {

	private final Logger logger = LogManager.getLogger(MinimumSampledCriterion.class);

	private final TerminationCriterion<X> delegate;
	private final long minSamples;
	private final long sampleInterval;
	private final long requiredPostStationarityIterations;

	private long iterations;
	private Long stationarityIteration;

	/**
	 * @param delegate       stationarity detector to wrap; receives every
	 *                       processState call through this class
	 * @param minSamples     minimum number of samples of the stationary
	 *                       distribution to draw before terminating
	 * @param sampleInterval spacing between samples in iterations (typically the
	 *                       logger message interval)
	 */
	public MinimumSampledCriterion(TerminationCriterion<X> delegate, long minSamples, long sampleInterval) {
		if (delegate == null) {
			throw new IllegalArgumentException("delegate must not be null");
		}
		if (minSamples <= 0 || sampleInterval <= 0) {
			throw new IllegalArgumentException("minSamples and sampleInterval must be positive, got minSamples="
					+ minSamples + ", sampleInterval=" + sampleInterval);
		}
		this.delegate = delegate;
		this.minSamples = minSamples;
		this.sampleInterval = sampleInterval;
		this.requiredPostStationarityIterations = minSamples * sampleInterval;
	}

	@Override
	public void start() {
		this.iterations = 0;
		this.stationarityIteration = null;
		this.delegate.start();
	}

	@Override
	public void processState(X state) {
		this.delegate.processState(state);
		this.registerIteration();
	}

	@Override
	public void processState(X state, double logWeight) {
		this.delegate.processState(state, logWeight);
		this.registerIteration();
	}

	@Override
	public void end() {
		this.delegate.end();
	}

	private void registerIteration() {
		this.iterations++;
		if (this.stationarityIteration == null && this.delegate.terminate()) {
			this.stationarityIteration = this.iterations;
			this.logger.info("{} reports stationarity at iteration {}; continuing for {} more iterations "
					+ "({} samples spaced {} iterations apart).", this.delegate.getClass().getSimpleName(),
					this.iterations, this.requiredPostStationarityIterations, this.minSamples, this.sampleInterval);
		}
	}

	@Override
	public boolean terminate() {
		if (this.stationarityIteration == null) {
			return false;
		}
		boolean done = (this.iterations - this.stationarityIteration) >= this.requiredPostStationarityIterations;
		if (done && !this.delegate.terminate()) {
			this.logger.warn("Terminating at iteration {}, but {} no longer reports stationarity "
					+ "(latched at iteration {}).", this.iterations, this.delegate.getClass().getSimpleName(),
					this.stationarityIteration);
		}
		return done;
	}
}
