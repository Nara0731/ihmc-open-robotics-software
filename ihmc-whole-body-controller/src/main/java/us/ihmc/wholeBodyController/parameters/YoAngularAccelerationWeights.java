package us.ihmc.wholeBodyController.parameters;

import us.ihmc.euclid.matrix.Matrix3D;
import us.ihmc.yoVariables.registry.YoRegistry;
import us.ihmc.yoVariables.variable.YoDouble;

public class YoAngularAccelerationWeights
{
   private final YoDouble yawAccelerationWeight, pitchAccelerationWeight, rollAccelerationWeight;

   public YoAngularAccelerationWeights(String prefix, YoRegistry registry)
   {
      yawAccelerationWeight = new YoDouble(prefix + "_YawAccelerationWeight", registry);
      pitchAccelerationWeight = new YoDouble(prefix + "_PitchAccelerationWeight", registry);
      rollAccelerationWeight = new YoDouble(prefix + "_RollAccelerationWeight", registry);
   }

   public void reset()
   {
      yawAccelerationWeight.set(0);
      pitchAccelerationWeight.set(0);
      rollAccelerationWeight.set(0);
   }

   public Matrix3D createAngularAccelerationWeightMatrix()
   {
      Matrix3D weightMatrix = new Matrix3D();

      yawAccelerationWeight.addListener(new MatrixUpdater(0, 0, weightMatrix));
      pitchAccelerationWeight.addListener(new MatrixUpdater(1, 1, weightMatrix));
      rollAccelerationWeight.addListener(new MatrixUpdater(2, 2, weightMatrix));

      yawAccelerationWeight.notifyListeners();
      pitchAccelerationWeight.notifyListeners();
      rollAccelerationWeight.notifyListeners();

      return weightMatrix;
   }

   public void setAngularAccelerationWeights(double yawAccelerationWeight, double pitchAccelerationWeight, double rollAccelerationWeight)
   {
      this.yawAccelerationWeight.set(yawAccelerationWeight);
      this.pitchAccelerationWeight.set(pitchAccelerationWeight);
      this.rollAccelerationWeight.set(rollAccelerationWeight);
   }

   public void setAngularAccelerationWeights(double weight)
   {
      this.yawAccelerationWeight.set(weight);
      this.pitchAccelerationWeight.set(weight);
      this.rollAccelerationWeight.set(weight);
   }

   public void setAngularAccelerationWeights(double[] weights)
   {
      yawAccelerationWeight.set(weights[0]);
      pitchAccelerationWeight.set(weights[1]);
      rollAccelerationWeight.set(weights[2]);
   }

   public void setYawAccelerationWeight(double weight)
   {
      yawAccelerationWeight.set(weight);
   }

   public void setPitchAccelerationWeight(double weight)
   {
      pitchAccelerationWeight.set(weight);
   }

   public void setRollAccelerationWeight(double weight)
   {
      rollAccelerationWeight.set(weight);
   }

   public double getYawAccelerationWeight()
   {
      return yawAccelerationWeight.getDoubleValue();
   }

   public double getPitchAccelerationWeight()
   {
      return pitchAccelerationWeight.getDoubleValue();
   }

   public double getRollAccelerationWeight()

   {
      return rollAccelerationWeight.getDoubleValue();
   }

   public void getAngularAccelerationWeights(double[] weightsToPack)
   {
      weightsToPack[0] = yawAccelerationWeight.getDoubleValue();
      weightsToPack[1] = pitchAccelerationWeight.getDoubleValue();
      weightsToPack[2] = rollAccelerationWeight.getDoubleValue();
   }
}
