package us.ihmc.footstepPlanning.simplePlanners;

import java.util.ArrayList;
import java.util.List;

import javax.vecmath.Point2d;

import us.ihmc.footstepPlanning.FootstepPlanner;
import us.ihmc.robotics.geometry.AngleTools;
import us.ihmc.robotics.geometry.FramePoint2d;
import us.ihmc.robotics.geometry.FramePose2d;
import us.ihmc.robotics.referenceFrames.Pose2dReferenceFrame;
import us.ihmc.robotics.referenceFrames.ReferenceFrame;
import us.ihmc.robotics.robotSide.RobotSide;

public class TurnWalkTurnPlanner implements FootstepPlanner
{

   private static final String STRAIGHT_PATH_NAME = "Forward Path";
   private static final double STRAIGHT_STEP_LENGTH = 0.4; // For Steppr: 0.30;
   private static final double STRAIGHT_STEP_WIDTH = 0.25; // For Steppr: 0.35;
   private static final String REVERSE_PATH_NAME = "Reverse Path";
   private static final double REVERSE_ANGLE = Math.PI;
   private static final double REVERSE_STEP_LENGTH = 0.15;
   private static final double REVERSE_STEP_WIDTH = 0.25; // For Steppr: 0.35;
   private static final String RIGHT_SHUFFLE_PATH_NAME = "Right Shuffle Path";
   private static final String LEFT_SHUFFLE_PATH_NAME = "Left Shuffle Path";
   private static final double SHUFFLE_STEP_LENGTH = 0.25;  // For Steppr: 0.3;
   private static final double SHUFFLE_STEP_WIDTH = 0.21;
   private static final double LEFT_SHUFFLE_ANGLE = -Math.PI / 2;

   public static double maximumHipOpeningAngle = Math.toRadians(20.0);
   public static double maximumHipClosingAngle = Math.toRadians(0.0);
   public static double turningStepWidth = 0.4;

   private final FramePose2d initialStanceFootPose = new FramePose2d();
   private final FramePose2d goalPose = new FramePose2d();
   private RobotSide lastStepSide;
   private final Pose2dReferenceFrame stanceFootFrame = new Pose2dReferenceFrame("StanceFootFrame", ReferenceFrame.getWorldFrame());
   private final Pose2dReferenceFrame turningFrame = new Pose2dReferenceFrame("TurningFrame", ReferenceFrame.getWorldFrame());

   @Override
   public void setInitialStanceFoot(FramePose2d stanceFootPose, RobotSide side)
   {
      this.initialStanceFootPose.setIncludingFrame(stanceFootPose);
      this.initialStanceFootPose.changeFrame(ReferenceFrame.getWorldFrame());
      this.lastStepSide = side;
   }

   @Override
   public void setGoalPose(FramePose2d goalPose)
   {
      this.goalPose.setIncludingFrame(goalPose);
      this.goalPose.changeFrame(ReferenceFrame.getWorldFrame());
   }

   @Override
   public List<FramePose2d> plan()
   {
      stanceFootFrame.setPoseAndUpdate(initialStanceFootPose);

      FramePoint2d goalPoint = new FramePoint2d();
      goalPose.getPosition(goalPoint);

      ArrayList<FramePose2d> footstepList = new ArrayList<>();

      // turn
      Point2d robotOffsetFromStanceFoot = new Point2d(0.0, lastStepSide.negateIfLeftSide(turningStepWidth / 2.0));
      FramePose2d robotPose = new FramePose2d(stanceFootFrame, robotOffsetFromStanceFoot, 0.0);
      FramePose2d robotPoseInWorld = new FramePose2d(robotPose);
      robotPoseInWorld.changeFrame(ReferenceFrame.getWorldFrame());
      addTurnInPlaceToFacePoint(footstepList, robotPose, goalPoint);

      // walk
      FramePoint2d robotPosition = new FramePoint2d();
      robotPoseInWorld.getPosition(robotPosition);
      double distanceToTravel = robotPosition.distance(goalPoint);
      addStraightWalk(footstepList, robotPosition, distanceToTravel);

      // turn
      FramePose2d stanceFootPose = new FramePose2d(stanceFootFrame);
      stanceFootPose.changeFrame(goalPose.getReferenceFrame());
      double turningAngle = AngleTools.trimAngleMinusPiToPi(goalPose.getYaw() - stanceFootPose.getYaw());
      FramePoint2d pointToTurnAbout = new FramePoint2d(stanceFootFrame, new Point2d(0.0, lastStepSide.negateIfLeftSide(STRAIGHT_STEP_WIDTH / 2.0)));
      addTurnInPlace(footstepList, turningAngle, pointToTurnAbout);

      // square up
      addSquareUp(footstepList, pointToTurnAbout);

      return footstepList;
   }

   private void addSquareUp(ArrayList<FramePose2d> footstepList, FramePoint2d robotPosition)
   {
      robotPosition.changeFrame(stanceFootFrame);
      if (Math.abs(robotPosition.getX()) > 0.001)
         throw new RuntimeException("Can not square up for given position.");

      robotPosition.changeFrame(stanceFootFrame);
      FramePose2d footstepPose = new FramePose2d(stanceFootFrame);
      footstepPose.setY(lastStepSide.negateIfLeftSide(2.0 * robotPosition.getY()));
      footstepPose.changeFrame(ReferenceFrame.getWorldFrame());

      footstepList.add(footstepPose);
      stanceFootFrame.setPoseAndUpdate(footstepPose);
      lastStepSide = lastStepSide.getOppositeSide();
   }

   private void addStraightWalk(ArrayList<FramePose2d> footstepList, FramePoint2d startingPoint, double distanceToTravel)
   {
      double straightSteps = Math.ceil(distanceToTravel / STRAIGHT_STEP_LENGTH);
      double stepLength = distanceToTravel / straightSteps;
      FramePoint2d startingPointInWorld = new FramePoint2d(startingPoint);
      startingPointInWorld.changeFrame(ReferenceFrame.getWorldFrame());

      for (int i = 0; i < straightSteps; i++)
      {
         startingPoint.setIncludingFrame(startingPointInWorld);
         startingPoint.changeFrame(stanceFootFrame);

         FramePose2d nextFootStep = new FramePose2d(stanceFootFrame);
         nextFootStep.setX(stepLength);
         nextFootStep.setY(startingPoint.getY() + lastStepSide.negateIfLeftSide(STRAIGHT_STEP_WIDTH/2.0));

         nextFootStep.changeFrame(ReferenceFrame.getWorldFrame());
         footstepList.add(nextFootStep);
         stanceFootFrame.setPoseAndUpdate(nextFootStep);
         lastStepSide = lastStepSide.getOppositeSide();
      }
   }

   private void addTurnInPlaceToFacePoint(ArrayList<FramePose2d> footstepList, FramePose2d robotPose, FramePoint2d goalPoint)
   {
      double turningAngle = AngleTools.calculateHeading(robotPose, goalPoint, 0.0, 0.0);
      FramePoint2d pointToTurnAbout = new FramePoint2d();
      robotPose.getPosition(pointToTurnAbout);
      addTurnInPlace(footstepList, turningAngle, pointToTurnAbout);
   }

   private void addTurnInPlace(ArrayList<FramePose2d> footstepList, double turningAngle, FramePoint2d pointToTurnAbout)
   {
      FramePoint2d pointToTurnAboutInWorld = new FramePoint2d(pointToTurnAbout);
      pointToTurnAboutInWorld.changeFrame(ReferenceFrame.getWorldFrame());

      pointToTurnAbout.changeFrame(stanceFootFrame);
      if (Math.abs(pointToTurnAbout.getX()) > 0.001)
         throw new RuntimeException("Can not turn in place around given point.");

      RobotSide sideToTurnTo = turningAngle >= 0.0 ? RobotSide.LEFT : RobotSide.RIGHT;

      double twoStepTurnAngle = maximumHipClosingAngle + maximumHipOpeningAngle;
      double requiredDoubleSteps = Math.abs(turningAngle / twoStepTurnAngle);

      double turningSteps = 2.0 * Math.ceil(requiredDoubleSteps);
      double maxTurningAngle = Math.ceil(requiredDoubleSteps) * twoStepTurnAngle;
      boolean firstStepClosing = sideToTurnTo.equals(lastStepSide);
      if (firstStepClosing)
      {
         if (Math.floor(requiredDoubleSteps) * twoStepTurnAngle + maximumHipClosingAngle >= turningAngle)
         {
            turningSteps--;
            maxTurningAngle -= maximumHipOpeningAngle;
         }
      }
      else
      {
         if (Math.floor(requiredDoubleSteps) * twoStepTurnAngle + maximumHipOpeningAngle >= turningAngle)
         {
            turningSteps--;
            maxTurningAngle -= maximumHipClosingAngle;
         }
      }
      double scaleTurningAngle = turningAngle / maxTurningAngle;

      for (int i = 0; i < turningSteps; i++)
      {
         FramePose2d turningFramePose = new FramePose2d(stanceFootFrame);
         pointToTurnAbout.setIncludingFrame(pointToTurnAboutInWorld);
         pointToTurnAbout.changeFrame(stanceFootFrame);
         turningFramePose.setY(pointToTurnAbout.getY());

         if (sideToTurnTo.equals(lastStepSide))
         {
            turningFramePose.setYaw(sideToTurnTo.negateIfRightSide(maximumHipClosingAngle * scaleTurningAngle));
         }
         else
         {
            turningFramePose.setYaw(sideToTurnTo.negateIfRightSide(maximumHipOpeningAngle * scaleTurningAngle));
         }
         turningFramePose.changeFrame(ReferenceFrame.getWorldFrame());
         turningFrame.setPoseAndUpdate(turningFramePose);

         FramePose2d nextFootstep = new FramePose2d(turningFrame);
         nextFootstep.setY(lastStepSide.negateIfLeftSide(turningStepWidth / 2.0));
         nextFootstep.changeFrame(ReferenceFrame.getWorldFrame());

         footstepList.add(nextFootstep);
         stanceFootFrame.setPoseAndUpdate(nextFootstep);
         lastStepSide = lastStepSide.getOppositeSide();
      }
   }

}
