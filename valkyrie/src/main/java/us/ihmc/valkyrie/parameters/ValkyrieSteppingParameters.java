package us.ihmc.valkyrie.parameters;

import us.ihmc.avatar.drcRobot.RobotTarget;
import us.ihmc.commonWalkingControlModules.configurations.SteppingParameters;

public class ValkyrieSteppingParameters implements SteppingParameters
{
   private final RobotTarget target;
   private final ValkyriePhysicalProperties physicalProperties;

   public ValkyrieSteppingParameters(ValkyriePhysicalProperties physicalProperties, RobotTarget target)
   {
      this.target = target;
      this.physicalProperties = physicalProperties;
   }

   @Override
   public double getFootForwardOffset()
   {
      return physicalProperties.getFootForward();
   }

   @Override
   public double getFootBackwardOffset()
   {
      return physicalProperties.getFootBack();
   }

   @Override
   public double getInPlaceWidth()
   {
      if (target == RobotTarget.SCS)
         return 0.25 * physicalProperties.getModelSizeScale();

      return 0.3 * physicalProperties.getModelSizeScale();
   }

   @Override
   public double getTurningStepWidth()
   {
      return 0.35 * physicalProperties.getModelSizeScale();
   }

   @Override
   public double getDesiredStepForward()
   {
      return 0.5 * physicalProperties.getModelSizeScale();
   }

   @Override
   public double getMaxStepLength()
   {
      if (target == RobotTarget.SCS)
         return 0.6 * physicalProperties.getModelSizeScale();

      return 0.4 * physicalProperties.getModelSizeScale();
   }

   @Override
   public double getDefaultStepLength()
   {
      return 0.5 * physicalProperties.getModelSizeScale();
   }

   @Override
   public double getMinStepWidth()
   {
      return ((target == RobotTarget.REAL_ROBOT) ? 0.165 : 0.15) * physicalProperties.getModelSizeScale();
   }

   @Override
   public double getMaxStepWidth()
   {
      return 0.4 * physicalProperties.getModelSizeScale();
   }

   @Override
   public double getStepPitch()
   {
      return 0.0;
   }

   @Override
   public double getMaxStepUp()
   {
      return 0.3 * physicalProperties.getModelSizeScale();
   }

   @Override
   public double getMaxStepDown()
   {
      return 0.25 * physicalProperties.getModelSizeScale();
   }

   @Override
   public double getMaxSwingHeightFromStanceFoot()
   {
      return 0.3 * physicalProperties.getModelSizeScale();
   }

   @Override
   public double getDefaultSwingHeightFromStanceFoot()
   {
      if (target == RobotTarget.REAL_ROBOT)
         return 0.05 * physicalProperties.getModelSizeScale(); //changed from 0.025 to 0.05 in 11/2018 to help with foot getting caught on pallet
      else
         return 0.1 * physicalProperties.getModelSizeScale();
   }

   @Override
   public double getMinSwingHeightFromStanceFoot()
   {
      return 0.025 * physicalProperties.getModelSizeScale();
   }

   @Override
   public double getMaxAngleTurnOutwards()
   {
      return Math.PI / 3.0;
   }

   @Override
   public double getMaxAngleTurnInwards()
   {
      return -Math.toRadians(30.0);
   }

   @Override
   public double getMinAreaPercentForValidFootstep()
   {
      return 0.5;
   }

   @Override
   public double getDangerAreaPercentForValidFootstep()
   {
      return 0.75;
   }

   @Override
   public double getFootWidth()
   {
      return physicalProperties.getFootWidth();
   }

   @Override
   public double getToeWidth()
   {
      return physicalProperties.getFootWidth();
   }

   @Override
   public double getFootLength()
   {
      return physicalProperties.getFootLength();
   }

   @Override
   public double getActualFootWidth()
   {
      return physicalProperties.getFootWidth();
   }

   @Override
   public double getActualFootLength()
   {
      return physicalProperties.getFootLength();
   }
}
