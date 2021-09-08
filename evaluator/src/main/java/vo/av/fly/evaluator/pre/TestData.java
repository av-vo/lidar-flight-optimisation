package vo.av.fly.evaluator.pre;

import javax.media.j3d.Transform3D;
import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Generate a simple test dataset which contains a flat roof elevated from the ground
 */
public class TestData {
    public static void main(String[] args) throws IOException {
        // Generate, rotate, project

        double scaleFactor = .25;

        int dx = 20, dy = 15, h = 10;
        int noXSteps = (int) Math.round(20/scaleFactor);
        int noYSteps = (int) Math.round(15/scaleFactor);
        //double noiseIn = 1;
        //double noiseOut = .05;

        //int lpd = 150;

        String outFN = "/home/vvo/scratch/flight-optimisation/data/pcloud/test.txt";

        ArrayList<Point3d> pList =new ArrayList();
        for (int i=-noXSteps/2; i<=noXSteps/2; i++){
            for(int j=-noYSteps/2; j<=noYSteps/2;j++){
                Point3d p = new Point3d(i,j,0);
                pList.add(p);
            }
        }

        BufferedWriter writer = new BufferedWriter(new FileWriter(outFN));

        Transform3D scale = new Transform3D();
        scale.setScale(scaleFactor);

        for(int i=-1;i<=1;i++){
            for(int j=-1; j<=1;j++){
                writeFace(writer,pList,
                        new Transform3D[]{
                                scale,
                                translate(i*dx,j*dy,(i==0 && j==0)? h : 0 )
                        },

                        0,0);
            }
        }
        writer.close();
    }

    private static Transform3D translate(double tx, double ty, double tz){
        Transform3D transform = new Transform3D();
        transform.setTranslation(new Vector3d(tx,ty,tz));
        return transform;
    }

    public static void writeFace(BufferedWriter writer, ArrayList<Point3d>pList,Transform3D[] transformers, double noiseIn, double noiseOut) throws IOException{
        for(Point3d p:pList){
            Transform3D noise = new Transform3D();
            Point3d pOut = new Point3d();

            noise.setTranslation(new Vector3d(noiseIn*(Math.random()-.5),
                    noiseIn*(Math.random()-.5),
                    noiseOut*(Math.random()-.5)));
            //System.out.println(noise);
            //noise.setScale(noiseIn);
            noise.transform(p,pOut);

            for(Transform3D transformer : transformers)
                transformer.transform(pOut);

            writer.write(String.format("%.3f,%.3f,%.3f,0\n", pOut.x,pOut.y,pOut.z));
        }
    }
}
