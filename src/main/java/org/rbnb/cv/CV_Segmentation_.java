package org.rbnb.cv;

import ij.gui.GenericDialog;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;
import ij.ImagePlus;
import ij.IJ;
import ij.process.FloatProcessor;
import ij.process.ColorProcessor;
import ij.gui.Roi;

public class CV_Segmentation_ implements PlugInFilter {

    private ImagePlus imp;
    private double TIME_STEP = 0.1;
    private int MAX_ITERATIONS = 5000;
    private double MU = 0.02;
    private double LAMBDA_1 = 2.0;
    private double LAMBDA_2 = 1.0;
    private double NU = 0.0;
    private int REINIT_FREQUENCY = 10;
    private double EPSILON = 1.0;

    @Override
    public int setup(String arg, ImagePlus imp) {
        this.imp = imp;
        return DOES_8G | DOES_16 | DOES_32;
    }

    @Override
    public void run(ImageProcessor ip) {

        if (!showDialog()) return;
        IJ.log("CV: Starting Chan–Vese with TIME_STEP=" + TIME_STEP +
                " MU=" + MU + " NU=" + NU + " eps_delta=" + EPSILON);

        FloatProcessor I = ip.convertToFloatProcessor();
        float[] arr = (float[]) I.getPixels();
        float max = 0;
        for (float f : arr) max = Math.max(max, f);
        for (int i = 0; i < arr.length; i++) arr[i] /= max;

        FloatProcessor phi = initializePhi(I);

        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            chanVeseIteration(I, phi);
            if (REINIT_FREQUENCY > 0 && (iter % REINIT_FREQUENCY) == 0) {
                reinitializePhi(phi);
            }
            if ((iter % Math.max(1, MAX_ITERATIONS/10)) == 0) {
                IJ.log(String.format("CV: iteration %d / %d", iter, MAX_ITERATIONS));
            }
        }

        displayZeroLevelSet(ip, phi, MAX_ITERATIONS);
    }

    /**
     * Initializes the level set function phi as a Signed Distance Function (SDF).
     * If an ROI exists, it uses the ROI boundary. Otherwise, it uses a central circle.
     */
    private FloatProcessor initializePhi(FloatProcessor I) {
        int w = I.getWidth();
        int h = I.getHeight();
        FloatProcessor phi = new FloatProcessor(w, h);
        float[] p = (float[]) phi.getPixels();

        Roi roi = imp.getRoi();
        boolean useRoi = (roi != null && roi.isArea());

        double cx = w / 2.0;
        double cy = h / 2.0;
        double r = 0.5 * Math.min(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double dist;
                if (useRoi) {
                    boolean inside = roi.contains(x, y);
                    dist = inside ? -5.0 : 5.0;
                } else {
                    dist = Math.sqrt((x - cx)*(x - cx) + (y - cy)*(y - cy)) - r;
                }
                p[y*w + x] = (float) dist;
            }
        }
        return phi;
    }


    private void chanVeseIteration(FloatProcessor I, FloatProcessor phi) {
        int w = phi.getWidth();
        int h = phi.getHeight();
        float[] ipix = (float[]) I.getPixels();
        float[] ph = (float[]) phi.getPixels();

        double sumIn = 0, sumOut = 0;
        int cntIn = 0, cntOut = 0;
        for (int i = 0; i < ph.length; i++) {
            if (ph[i] < 0) { sumIn += ipix[i]; cntIn++; }
            else { sumOut += ipix[i]; cntOut++; }
        }
        double c1 = (cntIn > 0 ? sumIn / cntIn : 0);
        double c2 = (cntOut > 0 ? sumOut / cntOut : 0);

        float[] newPhi = new float[w*h];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int idx = y*w + x;
                float ph0 = ph[idx];
                float I0 = ipix[idx];
                double delta = (EPSILON/ Math.PI)/(EPSILON*EPSILON + ph0*ph0);
                double K = curvaturePoint(ph, w, h, x, y);
                double insideTerm  = LAMBDA_1 * (I0 - c1)*(I0 - c1);
                double outsideTerm = LAMBDA_2 * (I0 - c2)*(I0 - c2);

                double dphi = TIME_STEP * delta * ( MU * K + insideTerm - outsideTerm + NU);
                newPhi[idx] = (float)(ph0 + dphi);
            }
        }
        System.arraycopy(newPhi, 0, ph, 0, ph.length);
    }

    private double curvaturePoint(float[] phi, int w, int h, int x, int y) {
        int xm1 = (x == 0 ? w-1 : x-1);
        int xp1 = (x == w-1 ? 0 : x+1);
        int ym1 = (y == 0 ? h-1 : y-1);
        int yp1 = (y == h-1 ? 0 : y+1);

        double px = (phi[y*w + xp1] - phi[y*w + xm1]) * 0.5;
        double py = (phi[yp1*w + x] - phi[ym1*w + x]) * 0.5;
        double pxx = phi[y*w + xp1] - 2*phi[y*w + x] + phi[y*w + xm1];
        double pyy = phi[yp1*w + x] - 2*phi[y*w + x] + phi[ym1*w + x];
        double pxy = (phi[yp1*w + xp1] - phi[yp1*w + xm1] - phi[ym1*w + xp1] + phi[ym1*w + xm1]) / 4.0;

        double denom = px*px + py*py;
        if (denom<=1e-12) return 0;
        return -(pxx*py*py - 2*px*py*pxy + pyy*px*px) / denom;
    }

    private void reinitializePhi(FloatProcessor phi) {
        int w = phi.getWidth();
        int h = phi.getHeight();
        float[] p = (float[]) phi.getPixels();
        float[] tmp = new float[p.length];

        double dt = 0.3;

        for (int iter = 0; iter < 5; iter++) {
            for (int y = 0; y < h; y++) {
                int ym1 = Math.max(y-1,0);
                int yp1 = Math.min(y+1,h-1);

                for (int x = 0; x < w; x++) {
                    int xm1 = Math.max(x-1,0);
                    int xp1 = Math.min(x+1,w-1);

                    int idx = y*w + x;
                    double ph0 = p[idx];

                    double DxF = p[y*w + xp1] - ph0;
                    double DxB = ph0 - p[y*w + xm1];
                    double DyF = p[yp1*w + x] - ph0;
                    double DyB = ph0 - p[ym1*w + x];

                    double gradPlus = Math.sqrt(
                            Math.max(DxB,0)*Math.max(DxB,0) +
                                    Math.min(DxF,0)*Math.min(DxF,0) +
                                    Math.max(DyB,0)*Math.max(DyB,0) +
                                    Math.min(DyF,0)*Math.min(DyF,0));

                    double gradMinus = Math.sqrt(
                            Math.min(DxB,0)*Math.min(DxB,0) +
                                    Math.max(DxF,0)*Math.max(DxF,0) +
                                    Math.min(DyB,0)*Math.min(DyB,0) +
                                    Math.max(DyF,0)*Math.max(DyF,0));

                    double S = ph0 / Math.sqrt(ph0*ph0 + 1.0);
                    double update = S * (1 - (S > 0 ? gradPlus : gradMinus));

                    tmp[idx] = (float)(ph0 + dt * update);
                }
            }
            System.arraycopy(tmp,0,p,0,p.length);
        }
    }

    private void displayZeroLevelSet(ImageProcessor originalIp, FloatProcessor phi, int iteration) {
        int w = phi.getWidth();
        int h = phi.getHeight();
        float[] ph = (float[]) phi.getPixels();
        FloatProcessor mask = new FloatProcessor(w, h);
        float[] m = (float[]) mask.getPixels();
        for (int i = 0; i < ph.length; i++) {
            m[i] = (ph[i] > 0 ? 1f : 0f);
        }
        ImagePlus result = new ImagePlus("Chan-Vese Result (iter " + iteration + ")", mask);
        result.show();

        ImageProcessor grayIp = originalIp.convertToByteProcessor();
        byte[] originalPixels = (byte[]) grayIp.getPixels();

        ColorProcessor cp = new ColorProcessor(w, h);
        int[] rgbPixels = (int[]) cp.getPixels();

        final int MASK_R = 0;
        final int MASK_G = 150;
        final int MASK_B = 150;
        final double ALPHA = 0.6;

        for (int i = 0; i < w * h; i++) {
            int gray = originalPixels[i] & 0xFF;
            int R = gray;
            int G = gray;
            int B = gray;

            if (ph[i] < 0) {
                R = (int) (ALPHA * MASK_R + (1.0 - ALPHA) * R);
                G = (int) (ALPHA * MASK_G + (1.0 - ALPHA) * G);
                B = (int) (ALPHA * MASK_B + (1.0 - ALPHA) * B);
                R = Math.min(255, Math.max(0, R));
                G = Math.min(255, Math.max(0, G));
                B = Math.min(255, Math.max(0, B));
            }
            rgbPixels[i] = (R << 16) | (G << 8) | B;
        }

        ImagePlus result2 = new ImagePlus("Chan-Vese Masked Image (iter " + iteration + ")", cp);
        result2.show();
    }

    private boolean showDialog() {
        GenericDialog gd = new GenericDialog("Chan–Vese");

        gd.addNumericField("Length term μ:", MU, 3);
        gd.addNumericField("Inflation ν:", NU, 5);
        gd.addNumericField("Inside λ1:", LAMBDA_1, 3);
        gd.addNumericField("Outside λ2:", LAMBDA_2, 3);

        gd.addMessage("Time settings");
        gd.addNumericField("Time step (tau):", TIME_STEP, 3);
        gd.addNumericField("Max iterations (optional fallback):", MAX_ITERATIONS, 0);

        gd.showDialog();
        if (gd.wasCanceled()) return false;

        MU = gd.getNextNumber();
        NU = gd.getNextNumber();
        LAMBDA_1 = gd.getNextNumber();
        LAMBDA_2 = gd.getNextNumber();
        TIME_STEP = gd.getNextNumber();
        MAX_ITERATIONS = (int) gd.getNextNumber();

        return true;
    }
}
