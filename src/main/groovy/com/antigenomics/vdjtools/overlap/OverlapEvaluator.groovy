/*
 * Copyright 2013-2015 Mikhail Shugay (mikhail.shugay@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.antigenomics.vdjtools.overlap

import com.antigenomics.vdjtools.basic.SegmentUsage
import com.antigenomics.vdjtools.basic.Spectratype
import com.antigenomics.vdjtools.join.JointSample
import com.antigenomics.vdjtools.sample.Sample
import com.antigenomics.vdjtools.util.ExecUtil
import com.antigenomics.vdjtools.util.MathUtil
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation
import sun.reflect.generics.reflectiveObjects.NotImplementedException

import static com.antigenomics.vdjtools.overlap.OverlapMetric.*

/**
 * A helper class to compute various overlap metrics for joint overlap
 */
class OverlapEvaluator {
    //This static field public but not final, and could be changed by malicious code or by accident from another package. The field could be made final to avoid this vulnerability.
    public static boolean VERBOSE = true

    private final JointSample jointSample
    private SegmentUsage segmentUsageCache
    private final Spectratype[] spectratypeCache
    private final Map<String, Double> metricCache = new HashMap<>()

    /**
     * Sets up an instance that can compute overlap metrics for a pre-defined set of sample intersections
     * @param jointSample a result of overlap between sample pair(s)
     */
    public OverlapEvaluator(JointSample jointSample) {
        this.jointSample = jointSample
        this.spectratypeCache = new Spectratype[jointSample.numberOfSamples]
    }

    /**
     * INTERNAL gets spectratype, for spectratype JSD metrics 
     * @param sampleIndex
     * @return
     */
    private Spectratype getSpectratype(int sampleIndex) {
        if (!spectratypeCache[sampleIndex]) {
            spectratypeCache[sampleIndex] = new Spectratype(jointSample.getSample(sampleIndex),
                    jointSample.overlapType,
                    false)
        }
        spectratypeCache[sampleIndex]
    }

    /**
     * INTERNAL gets segment usage, for V/J/V+J/V*J JSD metrics
     * @param sampleIndex
     * @return
     */
    private SegmentUsage getSegmentUsage() {
        if (!segmentUsageCache) {
            segmentUsageCache = new SegmentUsage((0..<jointSample.numberOfSamples).collect {
                jointSample.getSample(it)
            } as Sample[], false)
        }
        segmentUsageCache
    }

    /**
     * INTERNAL main routine that calculates a specified overlap metric
     * @param metric metric type
     * @param i index of first sample in pair
     * @param j index of second sample in pair
     * @return
     */
    private double _computeIntersectionMetric(OverlapMetric metric,
                                              int i, int j) {
        ExecUtil.report(this, "Computing $metric", VERBOSE)
        switch (metric) {
            case Diversity:
                def div1 = jointSample.getSample(i).diversity,
                        div2 = jointSample.getSample(j).diversity,
                        div12 = jointSample.getIntersectionDiv(i, j)
                return div12 / div1 / div2

            case Frequency:
                return Math.sqrt(jointSample.getIntersectionFreq(i, j) * jointSample.getIntersectionFreq(j, i))

            case Frequency2:
                double F2 = 0;
                jointSample.each {
                    F2 += Math.sqrt(it.getFreq(i) * it.getFreq(j))
                }
                return F2

            case Correlation:
                double R = 0

                int n = jointSample.getIntersectionDiv(i, j)

                if (n > 2) {
                    def x = new double[n],
                        y = new double[n]
                    int k = 0
                    jointSample.each {
                        if (it.present(i) && it.present(j)) {
                            x[k] = it.getFreq(i)
                            y[k] = it.getFreq(j)
                        }
                        k++
                    }

                    R = new PearsonsCorrelation().correlation(x, y)
                }

                return R

            case Jaccard:
                def div1 = jointSample.getSample(i).diversity,
                        div2 = jointSample.getSample(j).diversity,
                        div12 = jointSample.getIntersectionDiv(i, j)
                return div12 / (div1 + div2 - div12)

        /*
         case ChaoSorensen:
         case ChaoJaccard:
             double U1 = 0, U2 = 0, V1 = 0, V2 = 0,
                     f11 = 0, f12 = 0,
                     f21 = 0, f22 = 0;

             double n = jointSample.getSample(i).diversity,
                     m = jointSample.getSample(j).diversity

             jointSample.each {
                 if (it.present(i) && it.present(j)) {
                     int x = it.getCount(i), y = it.getCount(j)
                     if (x == 1) {
                         f11++
                         V2 += y
                     } else if (x == 2) {
                         f12++
                     }
                     if (y == 1) {
                         f21++
                         U2 += x
                     } else if (y == 2) {
                         f22++
                     }

                     U1 += x
                     V1 += y
                 }
             }

             f22 = f22 == 0 ? 1 : f22
             f12 = f12 == 0 ? 1 : f12

             double U = (U1 + f21 / f22 / 2 * (m - 1) / m * U2) / n,
                     V = (V1 + f11 / f12 / 2 * (n - 1) / n * V2) / m


             U = Math.min(U, 1)
             V = Math.min(V, 1)

             return U * V / (metric == ChaoSorensen ? ((U + V) / 2) : (U + V - U * V))
             */


            case MorisitaHorn:
                double xy = 0, Dx = 0, Dy = 0, x, y

                jointSample.each {
                    x = it.getFreq(i)
                    y = it.getFreq(j)
                    xy += x * y
                    Dx += x * x
                    Dy += y * y
                }

                return 2 * xy / (Dx + Dy)

            case vJSD:
                return MathUtil.JSD(
                        segmentUsage.vUsageVector(0),
                        segmentUsage.vUsageVector(1))

            case vjJSD:
                return MathUtil.JSD(
                        [segmentUsage.vUsageVector(0).collect(), segmentUsage.jUsageVector(0).collect()].flatten() as double[],
                        [segmentUsage.vUsageVector(1).collect(), segmentUsage.jUsageVector(1).collect()].flatten() as double[])

            case vj2JSD:
                return MathUtil.JSD(
                        segmentUsage.vjUsageMatrix(0).collect().flatten() as double[],
                        segmentUsage.vjUsageMatrix(1).collect().flatten() as double[])

            case sJSD:
                return MathUtil.JSD(getSpectratype(i).histogram,
                        getSpectratype(j).histogram)

            default:
                throw new NotImplementedException()
        }
    }

    /**
     * Computes specified overlap metric for a pair of samples
     * @param metric overlap metric type
     * @param i index of first sample in pair
     * @param j index of second sample in pair
     * @return overlap metric value
     */
    public double computeIntersectionMetric(OverlapMetric metric,
                                            int i, int j) {
        def key = [metric.shortName, i, j].join("_")
        def value = metricCache[key]
        if (!value)
            metricCache.put(key, value = _computeIntersectionMetric(metric, i, j))
        value
    }

    /**
     * Computes specified overlap metric for the first pair of samples
     * @param metric overlap metric type
     * @return overlap metric value
     */
    public double computeIntersectionMetric(OverlapMetric intersectMetric) {
        computeIntersectionMetric(intersectMetric, 0, 1)
    }
}
