package us.ihmc.darpaRoboticsChallenge.behaviorTests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;

import javax.vecmath.AxisAngle4d;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import us.ihmc.communication.kryo.IHMCCommunicationKryoNetClassList;
import us.ihmc.communication.net.PacketCommunicator;
import us.ihmc.communication.packetCommunicator.KryoLocalPacketCommunicator;
import us.ihmc.communication.packets.dataobjects.RobotConfigurationData;
import us.ihmc.communication.packets.manipulation.HandPosePacket.Frame;
import us.ihmc.communication.packets.manipulation.HandPoseStatus;
import us.ihmc.communication.subscribers.RobotDataReceiver;
import us.ihmc.communication.util.NetworkConfigParameters;
import us.ihmc.darpaRoboticsChallenge.DRCObstacleCourseStartingLocation;
import us.ihmc.darpaRoboticsChallenge.MultiRobotTestInterface;
import us.ihmc.darpaRoboticsChallenge.environment.DRCDemo01NavigationEnvironment;
import us.ihmc.darpaRoboticsChallenge.testTools.DRCSimulationTestHelper;
import us.ihmc.humanoidBehaviors.behaviors.primitives.HandPoseBehavior;
import us.ihmc.humanoidBehaviors.communication.BehaviorCommunicationBridge;
import us.ihmc.simulationconstructionset.Robot;
import us.ihmc.simulationconstructionset.bambooTools.BambooTools;
import us.ihmc.simulationconstructionset.util.simulationRunner.BlockingSimulationRunner.SimulationExceededMaximumTimeException;
import us.ihmc.utilities.MemoryTools;
import us.ihmc.utilities.SysoutTool;
import us.ihmc.utilities.ThreadTools;
import us.ihmc.utilities.humanoidRobot.model.ForceSensorDataHolder;
import us.ihmc.utilities.humanoidRobot.model.FullRobotModel;
import us.ihmc.utilities.math.geometry.FramePose;
import us.ihmc.utilities.math.geometry.ReferenceFrame;
import us.ihmc.utilities.math.geometry.RigidBodyTransform;
import us.ihmc.utilities.robotSide.RobotSide;
import us.ihmc.yoUtilities.dataStructure.variable.DoubleYoVariable;

public abstract class DRCHandPoseBehaviorTest implements MultiRobotTestInterface
{
   private static final boolean KEEP_SCS_UP = false;
   private static final boolean DEBUG = false;

   private final double POSITION_THRESHOLD = 0.007;
   private final double ORIENTATION_THRESHOLD = 0.007;
   private final double EXTRA_SIM_TIME_FOR_SETTLING = 2.0;

   private static final boolean createMovie = BambooTools.doMovieCreation();
   private static final boolean checkNothingChanged = BambooTools.getCheckNothingChanged();
   private static final boolean showGUI = KEEP_SCS_UP || createMovie;

   private final DRCDemo01NavigationEnvironment testEnvironment = new DRCDemo01NavigationEnvironment();
   private final PacketCommunicator controllerCommunicator = new KryoLocalPacketCommunicator(new IHMCCommunicationKryoNetClassList(), 10,
         "DRCHandPoseBehaviorTestControllerCommunicator");

   private DRCSimulationTestHelper drcSimulationTestHelper;

   private DoubleYoVariable yoTime;

   private RobotDataReceiver robotDataReceiver;
   private ForceSensorDataHolder forceSensorDataHolder;

   private BehaviorCommunicationBridge communicationBridge;

   private FullRobotModel fullRobotModel;

   private boolean returnValue = true;

   @Before
   public void setUp()
   {
      if (NetworkConfigParameters.USE_BEHAVIORS_MODULE)
      {
         throw new RuntimeException("Must set NetworkConfigParameters.USE_BEHAVIORS_MODULE = false in order to perform this test!");
      }

      MemoryTools.printCurrentMemoryUsageAndReturnUsedMemoryInMB(getClass().getSimpleName() + " before test.");

      drcSimulationTestHelper = new DRCSimulationTestHelper(testEnvironment, controllerCommunicator, getSimpleRobotName(), null,
            DRCObstacleCourseStartingLocation.DEFAULT, checkNothingChanged, showGUI, createMovie, false, getRobotModel());

      Robot robotToTest = drcSimulationTestHelper.getRobot();
      yoTime = robotToTest.getYoTime();

      fullRobotModel = getRobotModel().createFullRobotModel();

      forceSensorDataHolder = new ForceSensorDataHolder(Arrays.asList(fullRobotModel.getForceSensorDefinitions()));

      robotDataReceiver = new RobotDataReceiver(fullRobotModel, forceSensorDataHolder, true);
      controllerCommunicator.attachListener(RobotConfigurationData.class, robotDataReceiver);

      PacketCommunicator junkyObjectCommunicator = new KryoLocalPacketCommunicator(new IHMCCommunicationKryoNetClassList(), 10,
            "DRCHandPoseBehaviorTestJunkyCommunicator");

      communicationBridge = new BehaviorCommunicationBridge(junkyObjectCommunicator, controllerCommunicator, robotToTest.getRobotsYoVariableRegistry());

   }

   @After
   public void destroySimulationAndRecycleMemory()
   {
      if (KEEP_SCS_UP)
      {
         ThreadTools.sleepForever();
      }

      if (drcSimulationTestHelper != null)
      {
         drcSimulationTestHelper.destroySimulation();
         drcSimulationTestHelper = null;
      }

      MemoryTools.printCurrentMemoryUsageAndReturnUsedMemoryInMB(getClass().getSimpleName() + " after test.");
   }

   @Test(timeout = 300000)
   public void testSimpleHandPoseMove() throws SimulationExceededMaximumTimeException
   {
      BambooTools.reportTestStartedMessage();

      boolean success = drcSimulationTestHelper.simulateAndBlockAndCatchExceptions(1.0);

      double swingTrajectoryTime = 2.0;
      RobotSide robotSide = RobotSide.LEFT;
      FramePose handPoseStart = getCurrentHandPose(robotSide);

      FramePose handPoseTarget = new FramePose(handPoseStart);
      handPoseTarget.setZ(handPoseTarget.getZ() + 0.2);
      handPoseTarget.setOrientation(new double[] { 0.0, 0.0, 0.6 });

      final HandPoseBehavior handPoseBehavior = createNewHandPoseBehavior(robotSide, swingTrajectoryTime, handPoseTarget);

      final double simulationRunTime = swingTrajectoryTime + EXTRA_SIM_TIME_FOR_SETTLING;
      createAndStartBehaviorThread(handPoseBehavior, simulationRunTime);
      success = success && drcSimulationTestHelper.simulateAndBlockAndCatchExceptions(simulationRunTime);

      FramePose handPoseEnd = getCurrentHandPose(robotSide);

      assertPosesAreWithinThresholds(handPoseEnd, handPoseTarget);

      assertTrue(success);
      assertTrue(handPoseBehavior.isDone());

      BambooTools.reportTestFinishedMessage();
   }

   @Test(timeout = 300000)
   public void testHandPoseRotationOnly() throws SimulationExceededMaximumTimeException
   {
      BambooTools.reportTestStartedMessage();

      boolean success = drcSimulationTestHelper.simulateAndBlockAndCatchExceptions(1.0);

      RobotSide robotSide = RobotSide.LEFT;
      double swingTrajectoryTime = 2.0;

      FramePose handPoseInitial = getCurrentHandPose(robotSide);
      SysoutTool.println("Initial hand pose: " + handPoseInitial + "\n", DEBUG);

      FramePose handPoseTarget = new FramePose(handPoseInitial);

      AxisAngle4d targetAxisAngle4d = new AxisAngle4d();
      handPoseTarget.getOrientation(targetAxisAngle4d);
      targetAxisAngle4d.setAngle(targetAxisAngle4d.getAngle() + Math.toRadians(-15.0));

      handPoseTarget.setOrientation(targetAxisAngle4d);
      SysoutTool.println("Desired hand pose: " + handPoseTarget + "\n", DEBUG);

      final HandPoseBehavior handPoseBehavior = createNewHandPoseBehavior(robotSide, swingTrajectoryTime, handPoseTarget);

      final double simulationRunTime = swingTrajectoryTime + EXTRA_SIM_TIME_FOR_SETTLING;
      createAndStartBehaviorThread(handPoseBehavior, simulationRunTime);
      success = success && drcSimulationTestHelper.simulateAndBlockAndCatchExceptions(simulationRunTime);

      FramePose handPoseEnd = getCurrentHandPose(robotSide);

      double changeInOrientation = handPoseInitial.getOrientationDistance(handPoseEnd);
      SysoutTool.println("Change in hand orientation: " + changeInOrientation + "\n", DEBUG);

      assertPosesAreWithinThresholds(handPoseEnd, handPoseTarget);

      assertTrue(success);
      assertTrue(handPoseBehavior.isDone());

      BambooTools.reportTestFinishedMessage();
   }

   @Test(timeout = 300000)
   public void testUnreachableHandPoseMove() throws SimulationExceededMaximumTimeException
   {
      BambooTools.reportTestStartedMessage();

      boolean success = drcSimulationTestHelper.simulateAndBlockAndCatchExceptions(1.0);

      RobotSide robotSide = RobotSide.LEFT;
      FramePose handPoseStart = getCurrentHandPose(robotSide);

      FramePose handPoseTarget = new FramePose(handPoseStart);
      handPoseTarget.setX(handPoseTarget.getX() + 1.5);
      handPoseTarget.setOrientation(new double[] { 0.0, 0.0, 0.6 });

      double swingTrajectoryTime = 2.0;

      final HandPoseBehavior handPoseBehavior = createNewHandPoseBehavior(robotSide, swingTrajectoryTime, handPoseTarget);

      final double simulationRunTime = swingTrajectoryTime + EXTRA_SIM_TIME_FOR_SETTLING;
      createAndStartBehaviorThread(handPoseBehavior, simulationRunTime);
      success = success && drcSimulationTestHelper.simulateAndBlockAndCatchExceptions(simulationRunTime);
      assertTrue(success);

      double positionDistance = handPoseStart.getPositionDistance(handPoseTarget);
      double orientationDistance = handPoseStart.getOrientationDistance(handPoseTarget);

      boolean desiredHandPoseWasNotReached = positionDistance > POSITION_THRESHOLD || orientationDistance > ORIENTATION_THRESHOLD;

      assertTrue(desiredHandPoseWasNotReached);
      assertTrue(handPoseBehavior.isDone()); // hand pose should be done if elapsedTime > swingTrajectoryTime, even if desired pose was not reached

      BambooTools.reportTestFinishedMessage();
   }

   @Test(timeout = 300000)
   public void testTwoSimultaneousHandPoseBehaviors() throws SimulationExceededMaximumTimeException
   {
      BambooTools.reportTestStartedMessage();

      boolean success = drcSimulationTestHelper.simulateAndBlockAndCatchExceptions(1.0);

      double trajectoryTime = 2.0;

      FramePose handPoseDesiredLeft = offsetCurrentHandPose(RobotSide.LEFT, 0.0, 0.0, 0.1);
      FramePose handPoseDesiredRight = offsetCurrentHandPose(RobotSide.RIGHT, 0.0, 0.0, 0.1);

      HandPoseBehavior handPoseBehaviorLeft = createNewHandPoseBehavior(RobotSide.LEFT, trajectoryTime, handPoseDesiredLeft);
      HandPoseBehavior handPoseBehaviorRight = createNewHandPoseBehavior(RobotSide.RIGHT, trajectoryTime, handPoseDesiredRight);

      final double simulationRunTime = trajectoryTime + EXTRA_SIM_TIME_FOR_SETTLING;
      createAndStartBehaviorThread(handPoseBehaviorLeft, simulationRunTime);
      success = success && drcSimulationTestHelper.simulateAndBlockAndCatchExceptions(simulationRunTime);
      assertTrue(success);

      FramePose LeftHandPoseEnd = getCurrentHandPose(RobotSide.LEFT);
      FramePose RightHandPoseEnd = getCurrentHandPose(RobotSide.RIGHT);

      assertTrue(handPoseBehaviorLeft.isDone());
      assertTrue(handPoseBehaviorRight.isDone());

      assertPosesAreWithinThresholds(LeftHandPoseEnd, handPoseDesiredLeft);
      assertPosesAreWithinThresholds(RightHandPoseEnd, handPoseDesiredRight);

      BambooTools.reportTestFinishedMessage();
   }

   @Test(timeout = 300000)
   public void testHandPosePause() throws SimulationExceededMaximumTimeException
   {
      BambooTools.reportTestStartedMessage();

      boolean success = drcSimulationTestHelper.simulateAndBlockAndCatchExceptions(1.0);

      RobotSide robotSide = RobotSide.LEFT;
      FramePose handPoseStart = getCurrentHandPose(robotSide);

      FramePose handPoseTarget = new FramePose(handPoseStart);
      handPoseTarget.setZ(handPoseTarget.getZ() + 0.3);
      handPoseTarget.setOrientation(new double[] { 0.0, 0.0, 0.6 });

      final double swingTrajectoryTime = 4.0;

      final HandPoseBehavior handPoseBehavior = createNewHandPoseBehavior(robotSide, swingTrajectoryTime, handPoseTarget);

      final double secondsToPauseEarly = 2.0;
      final double simulationRunTime = swingTrajectoryTime - secondsToPauseEarly;

      setReturnValue(true);

      Thread behaviorThreadFirst = new Thread()
      {
         public void run()
         {
            double startTime = Double.NaN;
            boolean simStillRunning = true;
            boolean initalized = false;

            while (simStillRunning)
            {
               if (!initalized)
               {
                  startTime = yoTime.getDoubleValue();
                  initalized = true;
               }

               double timeSpentSimulating = yoTime.getDoubleValue() - startTime;
               simStillRunning = timeSpentSimulating < (simulationRunTime - 0.5);

               handPoseBehavior.doControl();

               HandPoseStatus.Status status = handPoseBehavior.getStatus();

               if ((status != null) && (timeSpentSimulating > 0.5))
               {
                  if ((timeSpentSimulating > 0.5) && (timeSpentSimulating < (swingTrajectoryTime - 0.5)))
                  {
                     if (status != HandPoseStatus.Status.STARTED)
                     {
                        System.out.println("behaviorThreadFirst: status should be STARTED, = " + status + ", timeSpentSimulating=" + timeSpentSimulating);
                        setReturnValue(false);
                     }
                  }
                  else if (timeSpentSimulating > (swingTrajectoryTime + 0.5))
                  {
                     if (status != HandPoseStatus.Status.COMPLETED)
                     {
                        System.out.println("behaviorThreadFirst: status should be STARTED, = " + status + ", timeSpentSimulating=" + timeSpentSimulating);
                        setReturnValue(false);
                     }

                  }
               }
            }

            handPoseBehavior.pause();

            if (DEBUG)
               System.out.println("testHandPosePause: behaviorThreadFirst done, pause command sent");
         }
      };

      behaviorThreadFirst.start();

      success = success && drcSimulationTestHelper.simulateAndBlockAndCatchExceptions(simulationRunTime);

      assertTrue(returnValue);

      FramePose handPoseAtPauseStart = getCurrentHandPose(robotSide);

      double timeToPause = 2.0;
      success = success && drcSimulationTestHelper.simulateAndBlockAndCatchExceptions(timeToPause);

      handPoseBehavior.doControl();
      assertTrue(handPoseBehavior.isDone());

      FramePose handPoseAtPauseEnd = getCurrentHandPose(robotSide);

      if (DEBUG)
         System.out.println("testSimpleHandPoseMove: checking start and end of pause");

      assertPosesAreWithinThresholds(handPoseAtPauseStart, handPoseAtPauseEnd);

      handPoseBehavior.resume();

      setReturnValue(true);

      Thread behaviorThreadSecond = new Thread()
      {
         public void run()
         {
            double startTime = Double.NaN;
            boolean simStillRunning = true;
            boolean initalized = false;

            while (simStillRunning)
            {
               if (!initalized)
               {
                  startTime = yoTime.getDoubleValue();
                  initalized = true;
               }

               double timeSpentSimulating = yoTime.getDoubleValue() - startTime;
               simStillRunning = timeSpentSimulating < (swingTrajectoryTime + 1.0);

               handPoseBehavior.doControl();

               HandPoseStatus.Status status = handPoseBehavior.getStatus();

               if ((status != null) && (timeSpentSimulating > 0.5))
               {
                  if ((timeSpentSimulating > 0.5) && (timeSpentSimulating < (swingTrajectoryTime - 0.5)))
                  {
                     if (status != HandPoseStatus.Status.STARTED)
                     {
                        System.out.println("behaviorThreadFirst: status should be STARTED, = " + status + ", timeSpentSimulating=" + timeSpentSimulating);
                        setReturnValue(false);
                     }

                  }
                  else if (timeSpentSimulating > (swingTrajectoryTime + 0.5))
                  {
                     if (status != HandPoseStatus.Status.COMPLETED)
                     {
                        System.out.println("behaviorThreadFirst: status should be STARTED, = " + status + ", timeSpentSimulating=" + timeSpentSimulating);
                        setReturnValue(false);
                     }

                  }
               }
            }

            if (DEBUG)
               System.out.println("testHandPosePause: behaviorThreadSecond done");
         }
      };

      behaviorThreadSecond.start();

      assertTrue(returnValue);

      success = success && drcSimulationTestHelper.simulateAndBlockAndCatchExceptions(swingTrajectoryTime + EXTRA_SIM_TIME_FOR_SETTLING);

      handPoseBehavior.doControl();
      HandPoseStatus.Status status = handPoseBehavior.getStatus();
      assertTrue(status.equals(HandPoseStatus.Status.COMPLETED));

      FramePose handPoseAtEnd = getCurrentHandPose(robotSide);

      if (DEBUG)
         System.out.println("testSimpleHandPoseMove: checking start and end");

      assertPosesAreWithinThresholds(handPoseAtEnd, handPoseTarget);

      assertTrue(success);
      assertTrue(handPoseBehavior.isDone());

      BambooTools.reportTestFinishedMessage();
   }

   @Test(timeout = 300000)
   public void testHandPoseMoveStopMidTrajectory() throws SimulationExceededMaximumTimeException
   {
      BambooTools.reportTestStartedMessage();

      boolean success = drcSimulationTestHelper.simulateAndBlockAndCatchExceptions(1.0);

      RobotSide robotSide = RobotSide.LEFT;
      FramePose handPoseStart = getCurrentHandPose(robotSide);

      FramePose handPoseTarget = new FramePose(handPoseStart);
      handPoseTarget.setZ(handPoseTarget.getZ() + 0.3);
      handPoseTarget.setOrientation(new double[] { 0.0, 0.0, 0.6 });

      double swingTrajectoryTime = 4.0;

      final HandPoseBehavior handPoseBehavior = createNewHandPoseBehavior(robotSide, swingTrajectoryTime, handPoseTarget);

      final double simulationRunTime = swingTrajectoryTime / 2.0;

      createAndStartBehaviorThread(handPoseBehavior, simulationRunTime);
      success = success && drcSimulationTestHelper.simulateAndBlockAndCatchExceptions(simulationRunTime);

      handPoseBehavior.stop();

      FramePose handPoseJustAfterStop = getCurrentHandPose(robotSide);

      final double simulationRunTime2 = 2.0;

      createAndStartBehaviorThread(handPoseBehavior, simulationRunTime2);
      success = success && drcSimulationTestHelper.simulateAndBlockAndCatchExceptions(simulationRunTime2);
      assertTrue(success);

      handPoseBehavior.doControl();
      HandPoseStatus.Status status = handPoseBehavior.getStatus();
      assertTrue(status.equals(HandPoseStatus.Status.COMPLETED));

      FramePose handPoseAfterResting = getCurrentHandPose(robotSide);
      assertPosesAreWithinThresholds(handPoseJustAfterStop, handPoseAfterResting);

      assertTrue(handPoseBehavior.isDone());

      BambooTools.reportTestFinishedMessage();
   }

   private HandPoseBehavior createNewHandPoseBehavior(RobotSide robotSide, double trajectoryTime, FramePose handPoseTarget)
   {
      HandPoseBehavior ret = new HandPoseBehavior(communicationBridge, yoTime);
      communicationBridge.attachGlobalListenerToController(ret.getControllerGlobalPacketConsumer());

      RigidBodyTransform handPoseTargetTransform = new RigidBodyTransform();
      handPoseTarget.getPose(handPoseTargetTransform);

      ret.initialize();
      ret.setInput(Frame.WORLD, handPoseTargetTransform, robotSide, trajectoryTime);

      return ret;
   }

   private FramePose offsetCurrentHandPose(RobotSide robotSide, double deltaX, double deltaY, double deltaZ)
   {
      FramePose handPoseStart = getCurrentHandPose(robotSide);

      FramePose ret = new FramePose(handPoseStart);
      ret.setX(ret.getX() + deltaX);
      ret.setY(ret.getY() + deltaY);
      ret.setZ(ret.getZ() + deltaZ);

      return ret;
   }

   private FramePose getCurrentHandPose(RobotSide robotSideToTest)
   {
      FramePose ret = new FramePose();
      fullRobotModel.updateFrames();
      ret.setToZero(fullRobotModel.getHandControlFrame(robotSideToTest));
      ret.changeFrame(ReferenceFrame.getWorldFrame());
      return ret;
   }

   private void createAndStartBehaviorThread(final ArrayList<HandPoseBehavior> handPoseBehaviors, final double simulationRunTime)
   {
      Thread behaviorThread = new Thread()
      {
         public void run()
         {
            {
               double startTime = Double.NaN;
               boolean simStillRunning = true;
               boolean initalized = false;

               while (simStillRunning)
               {
                  if (!initalized)
                  {
                     startTime = yoTime.getDoubleValue();
                     initalized = true;
                  }

                  double timeSpentSimulating = yoTime.getDoubleValue() - startTime;
                  simStillRunning = timeSpentSimulating < simulationRunTime;

                  for (HandPoseBehavior handPoseBehavior : handPoseBehaviors)
                  {
                     handPoseBehavior.doControl();
                  }
               }
            }
         }
      };

      behaviorThread.start();
   }

   private void createAndStartBehaviorThread(final HandPoseBehavior handPoseBehavior, final double simulationRunTime)
   {
      ArrayList<HandPoseBehavior> handPoseBehaviors = new ArrayList<HandPoseBehavior>();
      handPoseBehaviors.add(handPoseBehavior);

      createAndStartBehaviorThread(handPoseBehaviors, simulationRunTime);
   }

   private void setReturnValue(boolean value)
   {
      returnValue = value;
   }

   private void assertPosesAreWithinThresholds(FramePose framePose1, FramePose framePose2)
   {
      double positionDistance = framePose1.getPositionDistance(framePose2);
      double orientationDistance = framePose1.getOrientationDistance(framePose2);

      if (DEBUG)
      {
         System.out.println("testSimpleHandPoseMove: positionDistance=" + positionDistance);
         System.out.println("testSimpleHandPoseMove: orientationDistance=" + orientationDistance);
      }

      assertEquals(0.0, positionDistance, POSITION_THRESHOLD);
      assertEquals(0.0, orientationDistance, ORIENTATION_THRESHOLD);
   }

}
