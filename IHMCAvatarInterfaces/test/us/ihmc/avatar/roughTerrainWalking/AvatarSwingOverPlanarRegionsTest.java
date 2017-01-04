package us.ihmc.avatar.roughTerrainWalking;

import javax.vecmath.Point3d;
import javax.vecmath.Quat4d;
import javax.vecmath.Vector3d;

import org.junit.After;
import org.junit.Before;

import us.ihmc.avatar.DRCObstacleCourseStartingLocation;
import us.ihmc.avatar.DRCStartingLocation;
import us.ihmc.avatar.MultiRobotTestInterface;
import us.ihmc.avatar.drcRobot.DRCRobotModel;
import us.ihmc.avatar.testTools.DRCSimulationTestHelper;
import us.ihmc.commonWalkingControlModules.trajectories.SwingOverPlanarRegionsTrajectoryExpander;
import us.ihmc.graphics3DDescription.yoGraphics.YoGraphicsListRegistry;
import us.ihmc.humanoidRobotics.communication.packets.walking.FootstepDataListMessage;
import us.ihmc.humanoidRobotics.communication.packets.walking.FootstepDataMessage;
import us.ihmc.humanoidRobotics.communication.packets.walking.FootstepDataMessage.FootstepOrigin;
import us.ihmc.robotics.dataStructures.registry.YoVariableRegistry;
import us.ihmc.robotics.geometry.BoundingBox3d;
import us.ihmc.robotics.geometry.ConvexPolygon2d;
import us.ihmc.robotics.geometry.FramePose;
import us.ihmc.robotics.geometry.PlanarRegionsList;
import us.ihmc.robotics.geometry.PlanarRegionsListGenerator;
import us.ihmc.robotics.robotSide.RobotSide;
import us.ihmc.robotics.robotSide.SideDependentList;
import us.ihmc.robotics.trajectories.TrajectoryType;
import us.ihmc.simulationconstructionset.bambooTools.BambooTools;
import us.ihmc.simulationconstructionset.bambooTools.SimulationTestingParameters;
import us.ihmc.simulationconstructionset.util.environments.PlanarRegionsListDefinedEnvironment;
import us.ihmc.simulationconstructionset.util.simulationRunner.BlockingSimulationRunner.SimulationExceededMaximumTimeException;
import us.ihmc.tools.continuousIntegration.ContinuousIntegrationTools;
import us.ihmc.tools.thread.ThreadTools;

public abstract class AvatarSwingOverPlanarRegionsTest implements MultiRobotTestInterface
{
   private SimulationTestingParameters simulationTestingParameters = SimulationTestingParameters.createFromEnvironmentVariables();
   private DRCSimulationTestHelper drcSimulationTestHelper;

   public void testSwingOverPlanarRegions() throws SimulationExceededMaximumTimeException
   {
      String className = getClass().getSimpleName();
      
      double swingTime = 2.0;
      double transferTime = 0.8;
      double stepLength = 0.3;
      double stepWidth = 0.14;
      int steps = 10;

      PlanarRegionsListGenerator generator = new PlanarRegionsListGenerator();

      generator.translate(0.4, 0.0, -0.01);
      generator.addCubeReferencedAtCenter(2.0, 2.0, 0.00005);
      generator.translate(0.0, 0.14, 0.0);
      generator.addCubeReferencedAtBottomMiddle(0.1, 0.1, 0.1);
      generator.translate(0.73, -0.28, 0.0);
      generator.addCubeReferencedAtBottomMiddle(0.1, 0.1, 0.14);

      PlanarRegionsList planarRegionsList = generator.getPlanarRegionsList();

      PlanarRegionsListDefinedEnvironment environment = new PlanarRegionsListDefinedEnvironment(planarRegionsList, 1e-2, true);

      DRCStartingLocation startingLocation = DRCObstacleCourseStartingLocation.DEFAULT;
      DRCRobotModel robotModel = getRobotModel();
      drcSimulationTestHelper = new DRCSimulationTestHelper(environment, className, startingLocation, simulationTestingParameters, robotModel);
      ThreadTools.sleep(1000);
      drcSimulationTestHelper.getSimulationConstructionSet().setCameraPosition(8.0, -8.0, 5.0);
      drcSimulationTestHelper.getSimulationConstructionSet().setCameraFix(1.5, 0.0, 0.8);

      YoVariableRegistry registry = new YoVariableRegistry(getClass().getSimpleName());
      YoGraphicsListRegistry yoGraphicsListRegistry = new YoGraphicsListRegistry();
      SwingOverPlanarRegionsTrajectoryExpander swingOverPlanarRegionsTrajectoryExpander = new SwingOverPlanarRegionsTrajectoryExpander(robotModel.getWalkingControllerParameters(),
                                                                                                                                       registry,
                                                                                                                                       yoGraphicsListRegistry);

//      YoGraphicsList vectors = new YoGraphicsList("NormalVectors");
//      for (int i = 0; i < planarRegionsList.getNumberOfPlanarRegions(); i++)
//      {
//         PlanarRegion planarRegion = planarRegionsList.getPlanarRegion(i);
//         YoFramePoint planarRegionPointInWorld = new YoFramePoint("PlanarRegionPoint" + i, ReferenceFrame.getWorldFrame(), registry);
//         YoFrameVector surfaceNormal = new YoFrameVector("NormalVector" + i, ReferenceFrame.getWorldFrame(), registry);
//
//         RigidBodyTransform transformToWorld = new RigidBodyTransform();
//         Point3d translation = new Point3d();
//         planarRegion.getTransformToWorld(transformToWorld);
//         transformToWorld.getTranslation(translation);
//         planarRegionPointInWorld.set(translation);
//
//         Vector3d normal = new Vector3d();
//         environment.getTerrainObject3D().getHeightMapIfAvailable().heightAndNormalAt(translation.x, translation.y, translation.z, normal);
//         surfaceNormal.setVector(normal);
//
//         YoGraphicVector surfaceNormalGraphic = new YoGraphicVector("PlanarRegionSurfaceNormalGraphic" + i, planarRegionPointInWorld, surfaceNormal,
//                                                                    YoAppearance.Aqua());
//         vectors.add(surfaceNormalGraphic);
//      }
//
//      drcSimulationTestHelper.getSimulationConstructionSet().addYoGraphicsList(vectors, false);
//
//      HeightMapWithNormals heightMap = environment.getTerrainObject3D().getHeightMapIfAvailable();
//      Graphics3DObject heightMapGraphics = new Graphics3DObject();
//      heightMapGraphics.addHeightMap(heightMap, 300, 300, YoAppearance.DarkGreen());
//      drcSimulationTestHelper.getSimulationConstructionSet().addStaticLinkGraphics(heightMapGraphics);

      drcSimulationTestHelper.addChildRegistry(registry);
      drcSimulationTestHelper.getSimulationConstructionSet().addYoGraphicsListRegistry(yoGraphicsListRegistry);
      SideDependentList<ConvexPolygon2d> footPolygons = new SideDependentList<>();
      for (RobotSide side : RobotSide.values)
      {
         footPolygons.set(side, new ConvexPolygon2d(robotModel.getContactPointParameters().getFootContactPoints().get(side)));
      }

      drcSimulationTestHelper.simulateAndBlockAndCatchExceptions(0.5);

      FramePose stanceFootPose = new FramePose();
      FramePose swingStartPose = new FramePose();
      FramePose swingEndPose = new FramePose();

      stanceFootPose.setPosition(0.0, -stepWidth, 0.0);
      swingEndPose.setPosition(0.0, stepWidth, 0.0);

      FootstepDataListMessage footsteps = new FootstepDataListMessage(swingTime, transferTime);
      for (int i = 1; i <= steps; i++)
      {
         RobotSide robotSide = i % 2 == 0 ? RobotSide.LEFT : RobotSide.RIGHT;
         double footstepY = robotSide.negateIfRightSide(stepWidth);
         double footstepX = stepLength * i;
         Point3d location = new Point3d(footstepX, footstepY, 0.0);
         Quat4d orientation = new Quat4d(0.0, 0.0, 0.0, 1.0);
         FootstepDataMessage footstepData = new FootstepDataMessage(robotSide, location, orientation);
         footstepData.setOrigin(FootstepOrigin.AT_SOLE_FRAME);

         swingStartPose.set(stanceFootPose);
         stanceFootPose.set(swingEndPose);
         swingEndPose.setPosition(footstepX, footstepY, 0.0);
         swingOverPlanarRegionsTrajectoryExpander.expandTrajectoryOverPlanarRegions(footPolygons.get(robotSide), stanceFootPose, swingStartPose, swingEndPose,
                                                                                    planarRegionsList);
         footstepData.setTrajectoryType(TrajectoryType.CUSTOM);
         Point3d waypointOne = new Point3d();
         Point3d waypointTwo = new Point3d();
         swingOverPlanarRegionsTrajectoryExpander.getExpandedWaypoints().get(0).get(waypointOne);
         swingOverPlanarRegionsTrajectoryExpander.getExpandedWaypoints().get(1).get(waypointTwo);
         footstepData.setTrajectoryWaypoints(new Point3d[] {waypointOne, waypointTwo});

         footsteps.add(footstepData);
      }

      drcSimulationTestHelper.send(footsteps);
      double simulationTime = (swingTime + transferTime) * steps + 1.0;
      drcSimulationTestHelper.simulateAndBlockAndCatchExceptions(simulationTime);

      Point3d rootJointPosition = new Point3d(2.81, 0.0, 0.79);
      Vector3d epsilon = new Vector3d(0.05, 0.05, 0.05);
      Point3d min = new Point3d(rootJointPosition);
      Point3d max = new Point3d(rootJointPosition);
      min.sub(epsilon);
      max.add(epsilon);
      drcSimulationTestHelper.assertRobotsRootJointIsInBoundingBox(new BoundingBox3d(min, max));

      if (!ContinuousIntegrationTools.isRunningOnContinuousIntegrationServer())
      {
         ThreadTools.sleepForever();
      }
   }

   @Before
   public void showMemoryUsageBeforeTest()
   {
      BambooTools.reportTestStartedMessage(simulationTestingParameters.getShowWindows());
   }

   @After
   public void destroySimulationAndRecycleMemory()
   {
      if (simulationTestingParameters.getKeepSCSUp())
      {
         ThreadTools.sleepForever();
      }

      // Do this here in case a test fails. That way the memory will be recycled.
      if (drcSimulationTestHelper != null)
      {
         drcSimulationTestHelper.destroySimulation();
         drcSimulationTestHelper = null;
      }

      BambooTools.reportTestFinishedMessage(simulationTestingParameters.getShowWindows());
      simulationTestingParameters = null;
   }
}
