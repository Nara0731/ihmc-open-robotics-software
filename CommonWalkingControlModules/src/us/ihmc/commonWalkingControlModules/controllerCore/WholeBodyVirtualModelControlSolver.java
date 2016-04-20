package us.ihmc.commonWalkingControlModules.controllerCore;

import us.ihmc.SdfLoader.models.FullRobotModel;
import us.ihmc.commonWalkingControlModules.controllerCore.command.inverseDynamics.*;
import us.ihmc.commonWalkingControlModules.controllerCore.command.lowLevel.*;
import us.ihmc.commonWalkingControlModules.controllerCore.command.virtualModelControl.VirtualWrenchCommand;
import us.ihmc.commonWalkingControlModules.controllerCore.command.virtualModelControl.VirtualWrenchCommandList;
import us.ihmc.commonWalkingControlModules.momentumBasedController.PlaneContactWrenchProcessor;
import us.ihmc.commonWalkingControlModules.momentumBasedController.optimization.virtualModelControl.VirtualModelControlOptimizationControlModule;
import us.ihmc.commonWalkingControlModules.momentumBasedController.optimization.JointIndexHandler;
import us.ihmc.commonWalkingControlModules.momentumBasedController.optimization.MomentumControlModuleException;
import us.ihmc.commonWalkingControlModules.visualizer.WrenchVisualizer;
import us.ihmc.humanoidRobotics.bipedSupportPolygons.ContactablePlaneBody;
import us.ihmc.humanoidRobotics.model.CenterOfPressureDataHolder;
import us.ihmc.robotics.dataStructures.registry.YoVariableRegistry;
import us.ihmc.robotics.dataStructures.variable.DoubleYoVariable;
import us.ihmc.robotics.geometry.FrameVector;
import us.ihmc.robotics.math.frames.YoFrameVector;
import us.ihmc.robotics.referenceFrames.ReferenceFrame;
import us.ihmc.robotics.screwTheory.*;
import us.ihmc.simulationconstructionset.yoUtilities.graphics.YoGraphicsListRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class WholeBodyVirtualModelControlSolver
{
   private static final ReferenceFrame worldFrame = ReferenceFrame.getWorldFrame();

   private final YoVariableRegistry registry = new YoVariableRegistry(getClass().getSimpleName());

   private final FullRobotModel controllerModel;

   private final VirtualModelControlOptimizationControlModule optimizationControlModule;
   private final VirtualModelController virtualModelController;

   private final SixDoFJoint rootJoint;
   private final RootJointDesiredConfigurationData rootJointDesiredConfiguration = new RootJointDesiredConfigurationData();
   private final LowLevelOneDoFJointDesiredDataHolder lowLevelOneDoFJointDesiredDataHolder = new LowLevelOneDoFJointDesiredDataHolder();

   private final VirtualWrenchCommandList virtualWrenchCommandList = new VirtualWrenchCommandList();
   private final VirtualWrenchCommand virtualWrenchCommand = new VirtualWrenchCommand();
   private final TwistCalculator twistCalculator;
   private final Wrench tmpWrench = new Wrench();
   private final Twist tmpTwist = new Twist();

   private final OneDoFJoint[] controlledOneDoFJoints;
   private final InverseDynamicsJoint[] jointsToOptimizeFor;
   private final List<OneDoFJoint> jointsToComputeDesiredPositionFor = new ArrayList<>();
   private final DoubleYoVariable alphaPositionIntegration = new DoubleYoVariable("alphaPositionIntegration", registry);
   private final DoubleYoVariable alphaVelocityIntegration = new DoubleYoVariable("alphaVelocityIntegration", registry);
   private final DoubleYoVariable integrationMaxVelocity = new DoubleYoVariable("integrationMaxVelocity", registry);

   private final Wrench residualRootJointWrench = new Wrench();
   private final FrameVector residualRootJointForce = new FrameVector();
   private final FrameVector residualRootJointTorque = new FrameVector();
   private final YoFrameVector yoResidualRootJointForce = new YoFrameVector("residualRootJointForce", worldFrame, registry);
   private final YoFrameVector yoResidualRootJointTorque = new YoFrameVector("residualRootJointTorque", worldFrame, registry);

   private final double controlDT;

   public WholeBodyVirtualModelControlSolver(WholeBodyControlCoreToolbox toolbox, YoVariableRegistry parentRegistry)
   {
      controlDT = toolbox.getControlDT();
      twistCalculator = toolbox.getTwistCalculator();
      List<? extends ContactablePlaneBody> contactablePlaneBodies = toolbox.getContactablePlaneBodies();
      YoGraphicsListRegistry yoGraphicsListRegistry = toolbox.getYoGraphicsListRegistry();

      controllerModel = toolbox.getFullRobotModel();
      rootJoint = toolbox.getRobotRootJoint();
      virtualModelController = new VirtualModelController(toolbox);
      optimizationControlModule = new VirtualModelControlOptimizationControlModule(toolbox, registry);

      JointIndexHandler jointIndexHandler = toolbox.getJointIndexHandler();
      jointsToOptimizeFor = jointIndexHandler.getIndexedJoints();
      controlledOneDoFJoints = jointIndexHandler.getIndexedOneDoFJoints();
      lowLevelOneDoFJointDesiredDataHolder.registerJointsWithEmptyData(controlledOneDoFJoints);
      lowLevelOneDoFJointDesiredDataHolder.setJointsControlMode(controlledOneDoFJoints, LowLevelJointControlMode.FORCE_CONTROL);

      RigidBody[] endEffectors = toolbox.getEndEffectors();
      for (int i = 0; i < endEffectors.length; i++)
         virtualModelController.registerEndEffector(endEffectors[i]);

      alphaPositionIntegration.set(0.9996);
      alphaVelocityIntegration.set(0.95);
      integrationMaxVelocity.set(2.0);

      parentRegistry.addChild(registry);
   }

   public void reset()
   {
      virtualModelController.reset();
      virtualWrenchCommandList.clear();
      optimizationControlModule.initialize();
   }

   public void initialize()
   {
      // When you initialize into this controller, reset the estimator positions to current. Otherwise it might be in a bad state
      // where the feet are all jacked up. For example, after falling and getting back up.
      virtualModelController.compute();
      optimizationControlModule.initialize();
   }

   public void compute()
   {
      MomentumModuleSolution momentumModuleSolution;
      try
      {
         momentumModuleSolution = optimizationControlModule.compute();
      }
      catch (MomentumControlModuleException momentumControlModuleException)
      {
         // Don't crash and burn. Instead do the best you can with what you have.
         // Or maybe just use the previous ticks solution.
         momentumModuleSolution = momentumControlModuleException.getMomentumModuleSolution();
      }

      Map<RigidBody, Wrench> externalWrenchSolution = momentumModuleSolution.getExternalWrenchSolution();
      List<RigidBody> rigidBodiesWithExternalWrench = momentumModuleSolution.getRigidBodiesWithExternalWrench();

      // submit forces for stability
      for (RigidBody rigidBody : rigidBodiesWithExternalWrench)
      {
         virtualModelController.submitEndEffectorVirtualWrench(rigidBody, externalWrenchSolution.get(rigidBody));
      }
      // submit virtual wrenches for tracking
      for (int i = 0; i < virtualWrenchCommandList.getNumberOfCommands(); i++)
      {
         virtualWrenchCommand.set(virtualWrenchCommandList.getCommand(i));
         if (!rigidBodiesWithExternalWrench.contains(virtualWrenchCommand.getRigidBody()))
            virtualModelController.submitEndEffectorVirtualWrench(virtualWrenchCommand);
      }

      VirtualModelControlSolution virtualModelControlSolution = virtualModelController.compute();
      Map<OneDoFJoint, Double> jointTorquesSolution = virtualModelControlSolution.getJointTorques();

      for (OneDoFJoint joint : controlledOneDoFJoints)
      {
         if (jointTorquesSolution.containsKey(joint))
            joint.setTau(jointTorquesSolution.get(joint));
      }

      updateLowLevelData();

      rootJoint.getWrench(residualRootJointWrench);
      residualRootJointWrench.getAngularPartIncludingFrame(residualRootJointTorque);
      residualRootJointWrench.getLinearPartIncludingFrame(residualRootJointForce);
      yoResidualRootJointForce.setAndMatchFrame(residualRootJointForce);
      yoResidualRootJointTorque.setAndMatchFrame(residualRootJointTorque);
   }

   private void updateLowLevelData()
   {
      rootJointDesiredConfiguration.setDesiredAccelerationFromJoint(rootJoint);
      lowLevelOneDoFJointDesiredDataHolder.setDesiredTorqueFromJoints(controlledOneDoFJoints);
      // TODO when we have forward dynamics solver
      /*
      lowLevelOneDoFJointDesiredDataHolder.setDesiredAccelerationFromJoints(controlledOneDoFJoints);

      for (int i = 0; i < jointsToComputeDesiredPositionFor.size(); i++)
      {
         OneDoFJoint joint = jointsToComputeDesiredPositionFor.get(i);
         LowLevelJointData lowLevelJointData = lowLevelOneDoFJointDesiredDataHolder.getLowLevelJointData(joint);
         if (!lowLevelJointData.hasDesiredVelocity())
            lowLevelJointData.setDesiredVelocity(joint.getQd());
         if (!lowLevelJointData.hasDesiredPosition())
            lowLevelJointData.setDesiredPosition(joint.getQ());

         double desiredAcceleration = lowLevelJointData.getDesiredAcceleration();
         double desiredVelocity = lowLevelJointData.getDesiredVelocity();
         double desiredPosition = lowLevelJointData.getDesiredPosition();

         desiredVelocity *= alphaVelocityIntegration.getDoubleValue();
         desiredVelocity += desiredAcceleration * controlDT;
         desiredVelocity = MathTools.clipToMinMax(desiredVelocity, integrationMaxVelocity.getDoubleValue());
         desiredPosition += desiredVelocity * controlDT;

         double errorPosition = MathTools.clipToMinMax(desiredPosition - joint.getQ(), 0.2);
         desiredPosition = joint.getQ() + errorPosition;
         desiredPosition = MathTools.clipToMinMax(desiredPosition, joint.getJointLimitLower(), joint.getJointLimitUpper());
         desiredPosition = alphaPositionIntegration.getDoubleValue() * desiredPosition + (1.0 - alphaPositionIntegration.getDoubleValue()) * joint.getQ();
         desiredVelocity = (desiredPosition - lowLevelJointData.getDesiredPosition()) / controlDT;

         lowLevelJointData.setDesiredVelocity(desiredVelocity);
         lowLevelJointData.setDesiredPosition(desiredPosition);
      }
      */
   }

   public void submitVirtualModelControlCommandList(InverseDynamicsCommandList virtualModelControlCommandList)
   {
      while (virtualModelControlCommandList.getNumberOfCommands() > 0)
      {
         InverseDynamicsCommand<?> command = virtualModelControlCommandList.pollCommand();
         switch (command.getCommandType())
         {
         case TASKSPACE:
            convertAndAddSpatialAccelerationCommand((SpatialAccelerationCommand) command);
            break;
         case MOMENTUM:
            optimizationControlModule.submitMomentumRateCommand((MomentumRateCommand) command);
            break;
         case EXTERNAL_WRENCH:
            optimizationControlModule.submitExternalWrenchCommand((ExternalWrenchCommand) command);
            break;
         case JOINT_ACCELERATION_INTEGRATION:
            submitJointAccelerationIntegrationCommand((JointAccelerationIntegrationCommand) command);
            break;
         case COMMAND_LIST:
            submitVirtualModelControlCommandList((InverseDynamicsCommandList) command);
            break;
         default:
            throw new RuntimeException("The command type: " + command.getCommandType() + " is not handled by the Jacobian Transpose solver mode.");
         }
      }
   }

   private void convertAndAddSpatialAccelerationCommand(SpatialAccelerationCommand command)
   {
      RigidBody endEffector = command.getEndEffector();
      RigidBodyInertia inertia = endEffector.getInertia();
      twistCalculator.getTwistOfBody(tmpTwist, endEffector);
      inertia.computeDynamicWrenchInBodyCoordinates(tmpWrench, command.getSpatialAcceleration(), tmpTwist);

      virtualWrenchCommand.set(endEffector, tmpWrench, command.getSelectionMatrix());
      virtualWrenchCommandList.addCommand(virtualWrenchCommand);
   }

   private void submitJointAccelerationIntegrationCommand(JointAccelerationIntegrationCommand command)
   {
      for (int i = 0; i < command.getNumberOfJointsToComputeDesiredPositionFor(); i++)
      {
         OneDoFJoint jointToComputeDesiedPositionFor = command.getJointToComputeDesiredPositionFor(i);
         if (!jointsToComputeDesiredPositionFor.contains(jointToComputeDesiedPositionFor))
            jointsToComputeDesiredPositionFor.add(jointToComputeDesiedPositionFor);
      }
   }

   public LowLevelOneDoFJointDesiredDataHolder getOutput()
   {
      return lowLevelOneDoFJointDesiredDataHolder;
   }

   public RootJointDesiredConfigurationDataReadOnly getOutputForRootJoint()
   {
      return rootJointDesiredConfiguration;
   }

   public InverseDynamicsJoint[] getJointsToOptimizeFors()
   {
      return jointsToOptimizeFor;
   }
}
