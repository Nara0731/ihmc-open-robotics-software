package us.ihmc.simpleWholeBodyWalking;

<<<<<<< HEAD
import us.ihmc.commonWalkingControlModules.configurations.ICPWithTimeFreezingPlannerParameters;
=======
>>>>>>> 13a03c33b98... set up the simple walking state controller
import us.ihmc.commonWalkingControlModules.highLevelHumanoidControl.HighLevelControllerFactoryHelper;
import us.ihmc.commonWalkingControlModules.highLevelHumanoidControl.factories.HighLevelControllerStateFactory;
import us.ihmc.commonWalkingControlModules.highLevelHumanoidControl.highLevelStates.HighLevelControllerState;
import us.ihmc.humanoidRobotics.communication.packets.dataobjects.HighLevelControllerName;

public class SimpleWalkingControllerStateFactory implements HighLevelControllerStateFactory
{
   private SimpleWalkingControllerState walkingControllerState;
<<<<<<< HEAD
   private SimpleControlManagerFactory managerFactory;
   private ICPWithTimeFreezingPlannerParameters capturePointPlannerParameters;
   
   public SimpleWalkingControllerStateFactory(ICPWithTimeFreezingPlannerParameters capturePointPlannerParameters) {
      this.capturePointPlannerParameters = capturePointPlannerParameters; // Needed to pass ICPWithTimeFreezingPlannerParameters this way
   }
   
=======
   private final SimpleControlManagerFactory managerFactory;

   public SimpleWalkingControllerStateFactory(SimpleControlManagerFactory managerFactory)
   {
      this.managerFactory = managerFactory;
   }

>>>>>>> 13a03c33b98... set up the simple walking state controller
   @Override
   public HighLevelControllerState getOrCreateControllerState(HighLevelControllerFactoryHelper controllerFactoryHelper)
   {
      if (walkingControllerState == null)
      {
<<<<<<< HEAD
         managerFactory = new SimpleControlManagerFactory(controllerFactoryHelper.getHighLevelHumanoidControllerToolbox().getYoVariableRegistry());
         managerFactory.setHighLevelHumanoidControllerToolbox(controllerFactoryHelper.getHighLevelHumanoidControllerToolbox());
         managerFactory.setWalkingControllerParameters(controllerFactoryHelper.getWalkingControllerParameters());
         managerFactory.setCapturePointPlannerParameters(capturePointPlannerParameters);
         
=======
>>>>>>> 13a03c33b98... set up the simple walking state controller
         walkingControllerState = new SimpleWalkingControllerState(controllerFactoryHelper.getCommandInputManager(), controllerFactoryHelper.getStatusMessageOutputManager(),
                                                                   managerFactory, controllerFactoryHelper.getHighLevelHumanoidControllerToolbox(),
                                                                   controllerFactoryHelper.getHighLevelControllerParameters(),
                                                                   controllerFactoryHelper.getWalkingControllerParameters());
      }

      return walkingControllerState;
   }

   @Override
   public HighLevelControllerName getStateEnum()
   {
      return HighLevelControllerName.WALKING;
   }
}
