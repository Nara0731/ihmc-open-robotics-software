package us.ihmc.commonWalkingControlModules.capturePoint.smoothCMPBasedICPPlanner.AMGeneration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import us.ihmc.commonWalkingControlModules.capturePoint.smoothCMPBasedICPPlanner.WalkingTrajectoryType;
import us.ihmc.commonWalkingControlModules.capturePoint.smoothCMPBasedICPPlanner.CoPGeneration.CoPPlanningTools;
import us.ihmc.commonWalkingControlModules.capturePoint.smoothCMPBasedICPPlanner.CoPGeneration.CoPPointsInFoot;
import us.ihmc.commonWalkingControlModules.configurations.AngularMomentumEstimationParameters;
import us.ihmc.commonWalkingControlModules.configurations.CoPPointName;
import us.ihmc.commonWalkingControlModules.configurations.ICPPlannerParameters;
import us.ihmc.commons.lists.RecyclingArrayList;
import us.ihmc.euclid.referenceFrame.FramePoint3D;
import us.ihmc.euclid.referenceFrame.FrameVector3D;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.euclid.referenceFrame.interfaces.FixedFramePoint3DBasics;
import us.ihmc.euclid.referenceFrame.interfaces.FixedFrameVector3DBasics;
import us.ihmc.euclid.referenceFrame.interfaces.FramePoint3DReadOnly;
import us.ihmc.euclid.referenceFrame.interfaces.FrameVector3DReadOnly;
import us.ihmc.robotics.math.trajectories.FrameTrajectory3D;
import us.ihmc.robotics.math.trajectories.TrajectoryMathTools;
import us.ihmc.robotics.math.trajectories.YoSegmentedFrameTrajectory3D;
import us.ihmc.yoVariables.euclid.referenceFrame.YoFramePoint3D;
import us.ihmc.yoVariables.listener.YoVariableChangedListener;
import us.ihmc.yoVariables.registry.YoRegistry;
import us.ihmc.yoVariables.variable.YoBoolean;
import us.ihmc.yoVariables.variable.YoDouble;
import us.ihmc.yoVariables.variable.YoInteger;
import us.ihmc.yoVariables.variable.YoVariable;

/**
 * Estimates the angular momentum generated by the swing foot about the CoM during a footstep
 * Needs a footstep CoP plan. Uses the entry, exit and end CoPs defined in the CoP plan to calculate a segmented CoM trajectory
 * The CoM trajectory is then used along with the footstep plan to determine the angular momentum generated
 */
public class FootstepAngularMomentumPredictor implements AngularMomentumTrajectoryGeneratorInterface
{
   private static final ReferenceFrame worldFrame = ReferenceFrame.getWorldFrame();
   private static final FrameVector3D zeroVector = new FrameVector3D();
   private static final FramePoint3D zeroPoint = new FramePoint3D();
   private static final int maxNumberOfTrajectoryCoefficients = 7;
   private static final int numberOfSwingSegments = 3;
   private static final int numberOfTransferSegments = 2;

   private final boolean DEBUG;

   private final YoRegistry registry = new YoRegistry(getClass().getSimpleName());
   private final int maxNumberOfFootstepsToConsider;
   private final TrajectoryMathTools trajectoryMathTools;

   private final YoBoolean planSwingAngularMomentum;
   private final YoBoolean planTransferAngularMomentum;

   private final YoDouble gravityZ;
   private final YoDouble swingLegMass;
   private final YoDouble supportLegMass;
   private final YoDouble comHeight;

   private final RecyclingArrayList<CoPPointsInFoot> upcomingCoPsInFootsteps;
   private final YoInteger numberOfRegisteredFootsteps;

   private final List<AngularMomentumTrajectory> swingAngularMomentumTrajectories;
   private final List<AngularMomentumTrajectory> transferAngularMomentumTrajectories;

   private final FrameVector3D desiredAngularMomentum = new FrameVector3D();
   private final FrameVector3D desiredTorque = new FrameVector3D();
   private final FrameVector3D desiredRotatum = new FrameVector3D();

   private final FrameTrajectory3D segmentCoMPositionTrajectory;
   private final FrameTrajectory3D segmentCoMVelocityTrajectory;
   private final FrameTrajectory3D segmentSwingFootPositionTrajectory;
   private final FrameTrajectory3D segmentSwingFootVelocityTrajectory;
   private final FrameTrajectory3D segmentSupportFootPositionTrajectory;
   private final FrameTrajectory3D segmentSupportFootVelocityTrajectory;
   private final FrameTrajectory3D estimatedAngularMomentumTrajectory;
   private AngularMomentumTrajectoryInterface activeTrajectory;
   private double initialTime;

   private List<? extends FramePoint3DReadOnly> comInitialPositions;
   private List<? extends FramePoint3DReadOnly> comFinalPositions;
   private List<? extends FrameVector3DReadOnly> comInitialVelocities;
   private List<? extends FrameVector3DReadOnly> comFinalVelocities;
   private List<? extends FrameVector3DReadOnly> comInitialAccelerations;
   private List<? extends FrameVector3DReadOnly> comFinalAccelerations;

   private final FramePoint3D tempFramePoint1 = new FramePoint3D();
   private final FramePoint3D tempFramePoint2 = new FramePoint3D();
   private final FrameVector3D tempFrameVector = new FrameVector3D();

   // DEBUGGING
   private final YoFramePoint3D comPosDebug;
   private final YoFramePoint3D comVelDebug;
   private final YoFramePoint3D comAccDebug;
   private final YoFramePoint3D swingFootPosDebug;
   private final YoFramePoint3D swingFootVelDebug;
   private final YoFramePoint3D swingFootAccDebug;
   private final YoFramePoint3D supportFootPosDebug;
   private final YoFramePoint3D supportFootVelDebug;
   private final YoFramePoint3D supportFootAccDebug;
   private final List<TrajectoryDebug> transferCoMTrajectories;
   private final List<TrajectoryDebug> swingCoMTrajectories;
   private final List<TrajectoryDebug> transferSwingFootTrajectories;
   private final List<TrajectoryDebug> swingSwingFootTrajectories;
   private final List<TrajectoryDebug> transferSupportFootTrajectories;
   private final List<TrajectoryDebug> swingSupportFootTrajectories;
   private TrajectoryDebug activeCoMTrajectory;
   private TrajectoryDebug activeSwingFootTrajectory;
   private TrajectoryDebug activeSupportFootTrajectory;

   private int numberOfStepsToConsider;

   public FootstepAngularMomentumPredictor(String namePrefix, YoDouble omega0, int maxNumberOfFootstepsToConsider, YoRegistry parentRegistry)
   {
      this(namePrefix, omega0, false, maxNumberOfFootstepsToConsider, parentRegistry);
   }

   public FootstepAngularMomentumPredictor(String namePrefix, YoDouble omega0, boolean debug, int maxNumberOfFootstepsToConsider, YoRegistry parentRegistry)
   {
      this.DEBUG = debug;
      String fullPrefix = namePrefix + "AngularMomentum";
      this.trajectoryMathTools = new TrajectoryMathTools(2 * maxNumberOfTrajectoryCoefficients);
      this.gravityZ = new YoDouble("AngularMomentumGravityZ", parentRegistry);
      this.maxNumberOfFootstepsToConsider = maxNumberOfFootstepsToConsider;
      omega0.addListener(new YoVariableChangedListener()
      {
         @Override
         public void changed(YoVariable v)
         {
            comHeight.set(gravityZ.getDoubleValue() / (omega0.getDoubleValue() * omega0.getDoubleValue()));
         }
      });
      gravityZ.addListener(new YoVariableChangedListener()
      {
         @Override
         public void changed(YoVariable v)
         {
            comHeight.set(gravityZ.getDoubleValue() / (omega0.getDoubleValue() * omega0.getDoubleValue()));
         }
      });
      this.swingLegMass = new YoDouble(fullPrefix + "SwingFootMass", registry);
      this.supportLegMass = new YoDouble(fullPrefix + "SupportFootMass", registry);
      this.comHeight = new YoDouble(fullPrefix + "CoMHeight", registry);
      this.planSwingAngularMomentum = new YoBoolean(fullPrefix + "PlanSwingAngularMomentumWithPredictor", registry);
      this.planTransferAngularMomentum = new YoBoolean(fullPrefix + "PlanTransferAngularMomentumWithPredictor", registry);

      this.swingAngularMomentumTrajectories = new ArrayList<>(maxNumberOfFootstepsToConsider);
      this.transferAngularMomentumTrajectories = new ArrayList<>(maxNumberOfFootstepsToConsider + 1);

      if (DEBUG)
      {
         this.swingCoMTrajectories = new ArrayList<>(maxNumberOfFootstepsToConsider);
         this.transferCoMTrajectories = new ArrayList<>(maxNumberOfFootstepsToConsider + 1);
         this.swingSwingFootTrajectories = new ArrayList<>(maxNumberOfFootstepsToConsider);
         this.transferSwingFootTrajectories = new ArrayList<>(maxNumberOfFootstepsToConsider + 1);
         this.swingSupportFootTrajectories = new ArrayList<>(maxNumberOfFootstepsToConsider);
         this.transferSupportFootTrajectories = new ArrayList<>(maxNumberOfFootstepsToConsider + 1);
      }
      else
      {
         this.swingCoMTrajectories = null;
         this.transferCoMTrajectories = null;
         this.swingSwingFootTrajectories = null;
         this.transferSwingFootTrajectories = null;
         this.swingSupportFootTrajectories = null;
         this.transferSupportFootTrajectories = null;
      }

      this.numberOfRegisteredFootsteps = new YoInteger(fullPrefix + "NumberOfRegisteredFootsteps", registry);
      for (int i = 0; i < maxNumberOfFootstepsToConsider; i++)
      {
         AngularMomentumTrajectory swingTrajectory = new AngularMomentumTrajectory(numberOfSwingSegments, 2 * maxNumberOfTrajectoryCoefficients);
         swingAngularMomentumTrajectories.add(swingTrajectory);

         AngularMomentumTrajectory transferTrajectory = new AngularMomentumTrajectory(numberOfTransferSegments, 2 * maxNumberOfTrajectoryCoefficients);
         transferAngularMomentumTrajectories.add(transferTrajectory);

         if (DEBUG)
         {
            TrajectoryDebug swingCoMTrajectory = new TrajectoryDebug("SwingCoMTrajDebug" + i, numberOfSwingSegments, maxNumberOfTrajectoryCoefficients,
                                                                     registry);
            swingCoMTrajectories.add(swingCoMTrajectory);
            TrajectoryDebug transferCoMTrajectory = new TrajectoryDebug("TransferCoMTrajDebug" + i, numberOfTransferSegments, maxNumberOfTrajectoryCoefficients,
                                                                        registry);
            transferCoMTrajectories.add(transferCoMTrajectory);
            TrajectoryDebug swingSwingFootTrajectory = new TrajectoryDebug("SwingSwFootTrajDebug" + i, numberOfSwingSegments, maxNumberOfTrajectoryCoefficients,
                                                                           registry);
            swingSwingFootTrajectories.add(swingSwingFootTrajectory);
            TrajectoryDebug transferSwingFootTrajectory = new TrajectoryDebug("TransferSwFootTrajDebug" + i, numberOfTransferSegments,
                                                                              maxNumberOfTrajectoryCoefficients, registry);
            transferSwingFootTrajectories.add(transferSwingFootTrajectory);
            TrajectoryDebug swingSupportFootTrajectory = new TrajectoryDebug("SwingSpFootTrajDebug" + i, numberOfSwingSegments,
                                                                             maxNumberOfTrajectoryCoefficients, registry);
            swingSupportFootTrajectories.add(swingSupportFootTrajectory);
            TrajectoryDebug transferSupportFootTrajectory = new TrajectoryDebug("TransferSpFootTrajDebug" + i, numberOfTransferSegments,
                                                                                maxNumberOfTrajectoryCoefficients, registry);
            transferSupportFootTrajectories.add(transferSupportFootTrajectory);
         }
      }


      upcomingCoPsInFootsteps = new RecyclingArrayList<>(maxNumberOfFootstepsToConsider + 2, new CoPPointsInFootSupplier(fullPrefix));
      upcomingCoPsInFootsteps.clear();
      copPointsConstructed = true;

      AngularMomentumTrajectory transferTrajectory = new AngularMomentumTrajectory(numberOfTransferSegments, 2 * maxNumberOfTrajectoryCoefficients);
      transferAngularMomentumTrajectories.add(transferTrajectory);

      if (DEBUG)
      {
         TrajectoryDebug transferCoMTrajectory = new TrajectoryDebug("TransferCoMTrajDebug" + maxNumberOfFootstepsToConsider, numberOfTransferSegments,
                                                                     maxNumberOfTrajectoryCoefficients, registry);
         transferCoMTrajectories.add(transferCoMTrajectory);
         TrajectoryDebug transferSwingFootTrajectory = new TrajectoryDebug("TransferSwFootTrajDebug" + maxNumberOfFootstepsToConsider, numberOfTransferSegments,
                                                                           maxNumberOfTrajectoryCoefficients, registry);
         transferSwingFootTrajectories.add(transferSwingFootTrajectory);
         TrajectoryDebug transferSupportFootTrajectory = new TrajectoryDebug("TransferSpFootTrajDebug" + maxNumberOfFootstepsToConsider,
                                                                             numberOfTransferSegments, maxNumberOfTrajectoryCoefficients, registry);
         transferSupportFootTrajectories.add(transferSupportFootTrajectory);
      }

      this.segmentCoMPositionTrajectory = new FrameTrajectory3D(maxNumberOfTrajectoryCoefficients, worldFrame);
      this.segmentCoMVelocityTrajectory = new FrameTrajectory3D(maxNumberOfTrajectoryCoefficients, worldFrame);
      this.segmentSwingFootPositionTrajectory = new FrameTrajectory3D(2 * maxNumberOfTrajectoryCoefficients, worldFrame);
      this.segmentSwingFootVelocityTrajectory = new FrameTrajectory3D(maxNumberOfTrajectoryCoefficients, worldFrame);
      this.segmentSupportFootPositionTrajectory = new FrameTrajectory3D(2 * maxNumberOfTrajectoryCoefficients, worldFrame);
      this.segmentSupportFootVelocityTrajectory = new FrameTrajectory3D(maxNumberOfTrajectoryCoefficients, worldFrame);

      this.estimatedAngularMomentumTrajectory = new FrameTrajectory3D(2 * maxNumberOfTrajectoryCoefficients, worldFrame);
      if (DEBUG)
      {
         this.comPosDebug = new YoFramePoint3D("CoMPosViz", "", worldFrame, registry);
         this.comVelDebug = new YoFramePoint3D("CoMVelViz", "", worldFrame, registry);
         this.comAccDebug = new YoFramePoint3D("CoMAccViz", "", worldFrame, registry);
         this.swingFootPosDebug = new YoFramePoint3D("SwFPosViz", worldFrame, registry);
         this.swingFootVelDebug = new YoFramePoint3D("SwFVelViz", worldFrame, registry);
         this.swingFootAccDebug = new YoFramePoint3D("SwFAccViz", worldFrame, registry);
         this.supportFootPosDebug = new YoFramePoint3D("SpFPosViz", worldFrame, registry);
         this.supportFootVelDebug = new YoFramePoint3D("SpFVelViz", worldFrame, registry);
         this.supportFootAccDebug = new YoFramePoint3D("SpFAccViz", worldFrame, registry);
      }
      else
      {
         comPosDebug = null;
         comVelDebug = null;
         comAccDebug = null;
         swingFootPosDebug = null;
         swingFootVelDebug = null;
         swingFootAccDebug = null;
         supportFootPosDebug = null;
         supportFootVelDebug = null;
         supportFootAccDebug = null;
      }

      parentRegistry.addChild(registry);
   }

   private int copSegmentNumber = 0;
   private boolean copPointsConstructed = false;

   private class CoPPointsInFootSupplier implements Supplier<CoPPointsInFoot>
   {
      private final String fullPrefix;

      public CoPPointsInFootSupplier(String fullPrefix)
      {
         this.fullPrefix = fullPrefix;
      }

      @Override
      public CoPPointsInFoot get()
      {
         CoPPointsInFoot pointsInFoot;
         if (!copPointsConstructed)
            pointsInFoot = new CoPPointsInFoot(fullPrefix, copSegmentNumber, registry);
         else
            pointsInFoot = new CoPPointsInFoot(fullPrefix, copSegmentNumber, null);

         copSegmentNumber++;
         return pointsInFoot;
      }
   }

   public void initializeParameters(ICPPlannerParameters icpPlannerParameters, double totalMass, double gravityZ)
   {
      numberOfStepsToConsider = icpPlannerParameters.getNumberOfFootstepsToConsider();
      AngularMomentumEstimationParameters angularMomentumParameters = icpPlannerParameters.getAngularMomentumEstimationParameters();
      this.swingLegMass.set(totalMass * angularMomentumParameters.getPercentageSwingLegMass());
      this.supportLegMass.set(totalMass * angularMomentumParameters.getPercentageSupportLegMass());
      this.gravityZ.set(gravityZ);
      this.planSwingAngularMomentum.set(icpPlannerParameters.planSwingAngularMomentum());
      this.planTransferAngularMomentum.set(icpPlannerParameters.planTransferAngularMomentum());
   }

   @Override
   public void clear()
   {
      for (int i = 0; i < maxNumberOfFootstepsToConsider; i++)
      {
         swingAngularMomentumTrajectories.get(i).reset();
         transferAngularMomentumTrajectories.get(i).reset();
      }
      transferAngularMomentumTrajectories.get(maxNumberOfFootstepsToConsider).reset();

      for (int i = 0; i < upcomingCoPsInFootsteps.size(); i++)
         upcomingCoPsInFootsteps.get(i).reset();
      upcomingCoPsInFootsteps.clear();

      if (DEBUG)
      {
         transferCoMTrajectories.get(maxNumberOfFootstepsToConsider).reset();
         transferSwingFootTrajectories.get(maxNumberOfFootstepsToConsider).reset();
         transferSupportFootTrajectories.get(maxNumberOfFootstepsToConsider).reset();

         for (int i = 0 ; i < maxNumberOfFootstepsToConsider; i++)
         {
            swingCoMTrajectories.get(i).reset();
            transferCoMTrajectories.get(i).reset();
            swingSwingFootTrajectories.get(i).reset();
            transferSwingFootTrajectories.get(i).reset();
            swingSupportFootTrajectories.get(i).reset();
            transferSupportFootTrajectories.get(i).reset();
         }
      }
   }

   @Override
   public void addCopAndComSetpointsToPlan(List<CoPPointsInFoot> copLocations, List<? extends FramePoint3DReadOnly> comInitialPositions,
                                           List<? extends FramePoint3DReadOnly> comFinalPositions, List<? extends FrameVector3DReadOnly> comInitialVelocities,
                                           List<? extends FrameVector3DReadOnly> comFinalVelocities, List<? extends FrameVector3DReadOnly> comInitialAccelerations,
                                           List<? extends FrameVector3DReadOnly> comFinalAccelerations, int numberOfRegisteredFootsteps)
   {
      upcomingCoPsInFootsteps.clear();
      for (int i = 0; i < copLocations.size(); i++)
         upcomingCoPsInFootsteps.add().set(copLocations.get(i));
      this.numberOfRegisteredFootsteps.set(numberOfRegisteredFootsteps);
      this.comInitialPositions = comInitialPositions;
      this.comFinalPositions = comFinalPositions;
      this.comInitialVelocities = comInitialVelocities;
      this.comFinalVelocities = comFinalVelocities;
      this.comInitialAccelerations = comInitialAccelerations;
      this.comFinalAccelerations = comFinalAccelerations;
   }

   @Override
   public void update(double currentTime)
   {
      if (activeTrajectory != null)
         activeTrajectory.update(currentTime - initialTime, desiredAngularMomentum, desiredTorque, desiredRotatum);
      else
      {
         desiredAngularMomentum.setToZero();
         desiredTorque.setToZero();
         desiredRotatum.setToZero();
      }
      getPredictedCenterOfMassPosition(currentTime);
      getPredictedFeetPosition(currentTime);
   }

   @Override
   public void getDesiredAngularMomentum(FixedFrameVector3DBasics desiredAngularMomentumToPack, FixedFrameVector3DBasics desiredTorqueToPack)
   {
      desiredAngularMomentumToPack.set(desiredAngularMomentum);
      desiredTorqueToPack.set(desiredTorque);
   }

   @Override
   public void initializeForDoubleSupport(double currentTime, boolean isStanding)
   {
      initialTime = currentTime;

      if (!isStanding)
      {
         activeTrajectory = transferAngularMomentumTrajectories.get(0);
      }
      else
      {
         activeTrajectory = null;
      }

      if (DEBUG)
      {
         activeCoMTrajectory = transferCoMTrajectories.get(0);
         activeSwingFootTrajectory = transferSwingFootTrajectories.get(0);
         activeSupportFootTrajectory = transferSupportFootTrajectories.get(0);
      }
   }

   @Override
   public void initializeForSingleSupport(double currentTime)
   {
      initialTime = currentTime;
      activeTrajectory = swingAngularMomentumTrajectories.get(0);
      if (DEBUG)
      {
         activeCoMTrajectory = swingCoMTrajectories.get(0);
         activeSwingFootTrajectory = swingSwingFootTrajectories.get(0);
         activeSupportFootTrajectory = swingSupportFootTrajectories.get(0);
      }
   }

   @Override
   public void computeReferenceAngularMomentumStartingFromDoubleSupport(boolean initialTransfer, boolean standing)
   {
      setAngularMomentumTrajectoryForFootsteps(WalkingTrajectoryType.TRANSFER);
   }

   @Override
   public void computeReferenceAngularMomentumStartingFromSingleSupport()
   {
      setAngularMomentumTrajectoryForFootsteps(WalkingTrajectoryType.SWING);
   }

   private void setAngularMomentumTrajectoryForFootsteps(WalkingTrajectoryType initialWalkingPhase)
   {
      CoPPointsInFoot copPointsInFoot;
      double phaseTime = 0.0;
      int comIndex = 0;
      int footstepIndex = 0;

      // handle the current swing, if in the swing state
      if(initialWalkingPhase == WalkingTrajectoryType.SWING)
      {
         copPointsInFoot = upcomingCoPsInFootsteps.get(footstepIndex + 1);
         setFootTrajectoriesForPhase(footstepIndex, initialWalkingPhase);
         for(int j = CoPPlanningTools.getCoPPointIndex(copPointsInFoot.getCoPPointList(), CoPPointName.ENTRY_COP) + 1; j < copPointsInFoot.getNumberOfCoPPoints(); j++, comIndex++)
         {
            setCoMTrajectory(phaseTime, phaseTime + copPointsInFoot.get(j).getTime(), comIndex);
            setFootTrajectoriesForSegment(phaseTime, phaseTime + copPointsInFoot.get(j).getTime());
            if(DEBUG)
            {
               swingCoMTrajectories.get(footstepIndex).set(segmentCoMPositionTrajectory);
               swingSwingFootTrajectories.get(footstepIndex).set(segmentSwingFootPositionTrajectory);
               swingSupportFootTrajectories.get(footstepIndex).set(segmentSupportFootPositionTrajectory);
            }
            calculateAngularMomentumTrajectory(estimatedAngularMomentumTrajectory, initialWalkingPhase);
            swingAngularMomentumTrajectories.get(footstepIndex).set(estimatedAngularMomentumTrajectory);
            phaseTime += copPointsInFoot.get(j).getTime();
         }
         footstepIndex++;
         phaseTime = 0.0;
      }

      // Flamingo stance does not have a final transfer phase, only the initial CoP waypoint and the CoP waypoints for the current step
      if (footstepIndex >= upcomingCoPsInFootsteps.size() - 1)
         return;

      // handle each of the upcoming footsteps
      WalkingTrajectoryType currentWalkingPhase = WalkingTrajectoryType.TRANSFER;
      int numberOfSteps = Math.min(numberOfRegisteredFootsteps.getIntegerValue(), numberOfStepsToConsider);
      setFootTrajectoriesForPhase(footstepIndex, currentWalkingPhase);
      for(int stepIndex = footstepIndex; stepIndex < numberOfSteps; stepIndex++)
      {
         copPointsInFoot = upcomingCoPsInFootsteps.get(stepIndex + 1);
         for(int j = 0; j < copPointsInFoot.getNumberOfCoPPoints(); j++, comIndex++)
         {
            setCoMTrajectory(phaseTime, phaseTime + copPointsInFoot.get(j).getTime(), comIndex);
            setFootTrajectoriesForSegment(phaseTime, phaseTime + copPointsInFoot.get(j).getTime());
            if(DEBUG)
            {
               if(currentWalkingPhase == WalkingTrajectoryType.TRANSFER)
               {
                  transferCoMTrajectories.get(stepIndex).set(segmentCoMPositionTrajectory);
                  transferSwingFootTrajectories.get(stepIndex).set(segmentSwingFootPositionTrajectory);
                  transferSupportFootTrajectories.get(stepIndex).set(segmentSupportFootPositionTrajectory);
               }
               else if(currentWalkingPhase == WalkingTrajectoryType.SWING)
               {
                  swingCoMTrajectories.get(stepIndex).set(segmentCoMPositionTrajectory);
                  swingSwingFootTrajectories.get(stepIndex).set(segmentSwingFootPositionTrajectory);
                  swingSupportFootTrajectories.get(stepIndex).set(segmentSupportFootPositionTrajectory);
               }
            }
            calculateAngularMomentumTrajectory(estimatedAngularMomentumTrajectory, currentWalkingPhase);
            if(currentWalkingPhase == WalkingTrajectoryType.TRANSFER)
            {
               transferAngularMomentumTrajectories.get(stepIndex).set(estimatedAngularMomentumTrajectory);
            }
            else if(currentWalkingPhase == WalkingTrajectoryType.SWING)
            {
               swingAngularMomentumTrajectories.get(stepIndex).set(estimatedAngularMomentumTrajectory);
            }
            phaseTime += copPointsInFoot.get(j).getTime();
            if(copPointsInFoot.getCoPPointList().get(j) == CoPPointName.ENTRY_COP)
            {
               currentWalkingPhase = WalkingTrajectoryType.SWING;
               setFootTrajectoriesForPhase(stepIndex, currentWalkingPhase);
               phaseTime = 0.0;
            }
            else if(j >= copPointsInFoot.getNumberOfCoPPoints() - 1)
            {
               currentWalkingPhase = WalkingTrajectoryType.TRANSFER;
               phaseTime = 0.0;
               setFootTrajectoriesForPhase(stepIndex + 1, currentWalkingPhase);
            }
         }
      }

      // handle terminal transfer
      copPointsInFoot = upcomingCoPsInFootsteps.get(numberOfSteps + 1);
      setFootTrajectoriesForPhase(numberOfSteps, currentWalkingPhase);
      for(int j = 0; j < copPointsInFoot.getNumberOfCoPPoints(); j++, comIndex++)
      {
         setCoMTrajectory(phaseTime, phaseTime + copPointsInFoot.get(j).getTime(), comIndex);
         setFootTrajectoriesForSegment(phaseTime, phaseTime + copPointsInFoot.get(j).getTime());
         if(DEBUG)
         {
               transferCoMTrajectories.get(numberOfSteps).set(segmentCoMPositionTrajectory);
               transferSwingFootTrajectories.get(numberOfSteps).set(segmentSwingFootPositionTrajectory);
               transferSupportFootTrajectories.get(numberOfSteps).set(segmentSupportFootPositionTrajectory);
         }
         calculateAngularMomentumTrajectory(estimatedAngularMomentumTrajectory, currentWalkingPhase);
         transferAngularMomentumTrajectories.get(numberOfSteps).set(estimatedAngularMomentumTrajectory);
         phaseTime += copPointsInFoot.get(j).getTime();
      }
   }

   private void setCoMTrajectory(double initialTime, double finalTime, int comIndex)
   {
      tempFramePoint1.set(comInitialPositions.get(comIndex));
      tempFramePoint1.addZ(comHeight.getDoubleValue());
      tempFramePoint2.set(comFinalPositions.get(comIndex));
      tempFramePoint2.addZ(comHeight.getDoubleValue());
      segmentCoMPositionTrajectory.setQuintic(initialTime, finalTime, tempFramePoint1, comInitialVelocities.get(comIndex), comInitialAccelerations.get(comIndex),
                                              tempFramePoint2, comFinalVelocities.get(comIndex), comFinalAccelerations.get(comIndex));
      TrajectoryMathTools.getDerivative(segmentCoMVelocityTrajectory, segmentCoMPositionTrajectory);
   }

   private void setFootTrajectoriesForSegment(double initialTime, double finalTime)
   {
      segmentSwingFootPositionTrajectory.setTime(initialTime, finalTime);
      TrajectoryMathTools.getDerivative(segmentSwingFootVelocityTrajectory, segmentSwingFootPositionTrajectory);

      segmentSupportFootPositionTrajectory.setTime(initialTime, finalTime);
      TrajectoryMathTools.getDerivative(segmentSupportFootVelocityTrajectory, segmentSupportFootPositionTrajectory);
   }

   private void setFootTrajectoriesForPhase(int footstepIndex, WalkingTrajectoryType phase)
   {
      CoPPointsInFoot copPointsInFoot = upcomingCoPsInFootsteps.get(footstepIndex + 1);
      double phaseDuration = 0.0;
      if(phase == WalkingTrajectoryType.SWING)
      {
         for(int j = CoPPlanningTools.getCoPPointIndex(copPointsInFoot.getCoPPointList(), CoPPointName.ENTRY_COP) + 1; j < copPointsInFoot.getNumberOfCoPPoints(); j++)
            phaseDuration += copPointsInFoot.get(j).getTime();
      }
      else
      {
         int j = 0;
         for(; j < copPointsInFoot.getNumberOfCoPPoints() - 1 && copPointsInFoot.getCoPPointList().get(j) != CoPPointName.ENTRY_COP
               && copPointsInFoot.getCoPPointList().get(j) != CoPPointName.FINAL_COP; j++)
            phaseDuration += copPointsInFoot.get(j).getTime();
         phaseDuration += copPointsInFoot.get(j).getTime();
      }
      setSwingFootTrajectoryForPhase(footstepIndex, phase, phaseDuration);
      setSupportFootTrajectoryForPhase(footstepIndex, phase, phaseDuration);
   }

   private void setSwingFootTrajectoryForPhase(int footstepIndex, WalkingTrajectoryType phase, double phaseDuration)
   {
      if(phase == WalkingTrajectoryType.SWING)
      {
         upcomingCoPsInFootsteps.get(footstepIndex).getSupportFootLocation(tempFramePoint1);
         upcomingCoPsInFootsteps.get(footstepIndex + 1).getSwingFootLocation(tempFramePoint2);
         segmentSwingFootPositionTrajectory.setQuinticWithZeroTerminalAcceleration(0.0, phaseDuration, tempFramePoint1, zeroVector, tempFramePoint2, zeroVector);
      }
      else
      {
         upcomingCoPsInFootsteps.get(footstepIndex).getSupportFootLocation(tempFramePoint1);
         segmentSwingFootPositionTrajectory.setConstant(0.0, phaseDuration, tempFramePoint1);
      }
   }

   private void setSupportFootTrajectoryForPhase(int footstepIndex, WalkingTrajectoryType phase, double phaseDuration)
   {
      upcomingCoPsInFootsteps.get(footstepIndex + 1).getSupportFootLocation(tempFramePoint1);
      segmentSupportFootPositionTrajectory.setConstant(0.0, phaseDuration, tempFramePoint1);
   }

   private final FrameTrajectory3D relativeSwingFootTrajectory = new FrameTrajectory3D(2 * maxNumberOfTrajectoryCoefficients, worldFrame);
   private final FrameTrajectory3D relativeSwingFootVelocity = new FrameTrajectory3D(2 * maxNumberOfTrajectoryCoefficients, worldFrame);
   private final FrameTrajectory3D swingFootMomentumTrajectory = new FrameTrajectory3D(2 * maxNumberOfTrajectoryCoefficients, worldFrame);

   private final FrameTrajectory3D relativeStanceFootTrajectory = new FrameTrajectory3D(2 * maxNumberOfTrajectoryCoefficients, worldFrame);
   private final FrameTrajectory3D relativeStanceFootVelocity = new FrameTrajectory3D(2 * maxNumberOfTrajectoryCoefficients, worldFrame);
   private final FrameTrajectory3D stanceFootMomentumTrajectory = new FrameTrajectory3D(2 * maxNumberOfTrajectoryCoefficients, worldFrame);

   private void calculateAngularMomentumTrajectory(FrameTrajectory3D estimatedAngularMomentumTrajectoryToPack, WalkingTrajectoryType phase)
   {
      if(calculateAngularMomentumForPhase(phase))
      {
         computeFootMomentum(swingFootMomentumTrajectory, relativeSwingFootTrajectory, relativeSwingFootVelocity, segmentSwingFootPositionTrajectory,
                             segmentSwingFootVelocityTrajectory, segmentCoMPositionTrajectory, segmentCoMVelocityTrajectory, swingLegMass.getDoubleValue(),
                             trajectoryMathTools);
         computeFootMomentum(stanceFootMomentumTrajectory, relativeStanceFootTrajectory, relativeStanceFootVelocity, segmentSupportFootPositionTrajectory,
                             segmentSupportFootVelocityTrajectory, segmentCoMPositionTrajectory, segmentCoMVelocityTrajectory, supportLegMass.getDoubleValue(),
                             trajectoryMathTools);

         TrajectoryMathTools.add(estimatedAngularMomentumTrajectoryToPack, stanceFootMomentumTrajectory, swingFootMomentumTrajectory);
      }
      else
      {
         double t0 = segmentSwingFootPositionTrajectory.getInitialTime();
         double tf = segmentSwingFootPositionTrajectory.getFinalTime();
         estimatedAngularMomentumTrajectoryToPack.setConstant(t0, tf, zeroPoint);
      }
   }

   private static void computeFootMomentum(FrameTrajectory3D momentumTrajectoryToPack, FrameTrajectory3D relativePositionTrajectoryToPack,
                                           FrameTrajectory3D relativeVelocityTrajectoryToPack, FrameTrajectory3D footPositionTrajectory,
                                           FrameTrajectory3D footVelocityTrajectory, FrameTrajectory3D comPositionTrajectory,
                                           FrameTrajectory3D comVelocityTrajectory, double mass, TrajectoryMathTools trajectoryMathTools)
   {
      TrajectoryMathTools.subtract(relativePositionTrajectoryToPack, footPositionTrajectory, comPositionTrajectory);
      TrajectoryMathTools.subtract(relativeVelocityTrajectoryToPack, footVelocityTrajectory, comVelocityTrajectory);
      trajectoryMathTools.crossProduct(momentumTrajectoryToPack, relativePositionTrajectoryToPack, relativeVelocityTrajectoryToPack);
      TrajectoryMathTools.scale(mass, momentumTrajectoryToPack);
   }

   public void getPredictedCenterOfMassPosition(FixedFramePoint3DBasics pointToPack, double time)
   {
      if (DEBUG)
      {
         activeCoMTrajectory.update(time - initialTime);
         activeCoMTrajectory.getFramePosition(tempFramePoint1);
         pointToPack.set(tempFramePoint1);
         comPosDebug.set(tempFramePoint1);
         activeCoMTrajectory.getFrameVelocity(tempFrameVector);
         comVelDebug.set(tempFrameVector);
         activeCoMTrajectory.getFrameAcceleration(tempFrameVector);
         comAccDebug.set(tempFrameVector);
      }
   }

   public void getPredictedCenterOfMassPosition(double time)
   {
      if (DEBUG)
      {
         activeCoMTrajectory.update(time - initialTime);
         activeCoMTrajectory.getFramePosition(tempFramePoint1);
         comPosDebug.set(tempFramePoint1);
         activeCoMTrajectory.getFrameVelocity(tempFrameVector);
         comVelDebug.set(tempFrameVector);
         activeCoMTrajectory.getFrameAcceleration(tempFrameVector);
         comAccDebug.set(tempFrameVector);
      }
   }

   public void getPredictedFootPosition(FixedFramePoint3DBasics pointToPack, double time)
   {
      if (DEBUG)
      {
         activeSwingFootTrajectory.update(time - initialTime);
         activeSwingFootTrajectory.getFramePosition(tempFramePoint1);
         swingFootPosDebug.set(tempFramePoint1);
         pointToPack.set(tempFramePoint1);
         activeSwingFootTrajectory.getFrameVelocity(tempFrameVector);
         swingFootVelDebug.set(tempFrameVector);
         activeSwingFootTrajectory.getFrameAcceleration(tempFrameVector);
         swingFootAccDebug.set(tempFrameVector);

         activeSupportFootTrajectory.update(time - initialTime);
         activeSupportFootTrajectory.getFramePosition(tempFramePoint1);
         supportFootPosDebug.set(tempFramePoint1);
         activeSupportFootTrajectory.getFrameVelocity(tempFrameVector);
         supportFootVelDebug.set(tempFrameVector);
         activeSupportFootTrajectory.getFrameAcceleration(tempFrameVector);
         supportFootAccDebug.set(tempFrameVector);
      }
   }

   public void getPredictedFeetPosition(double time)
   {
      if (DEBUG)
      {
         activeSwingFootTrajectory.update(time - initialTime);
         activeSwingFootTrajectory.getFramePosition(tempFramePoint1);
         swingFootPosDebug.set(tempFramePoint1);
         activeSwingFootTrajectory.getFrameVelocity(tempFrameVector);
         swingFootVelDebug.set(tempFrameVector);
         activeSwingFootTrajectory.getFrameAcceleration(tempFrameVector);
         swingFootAccDebug.set(tempFrameVector);

         activeSupportFootTrajectory.update(time - initialTime);
         activeSupportFootTrajectory.getFramePosition(tempFramePoint1);
         supportFootPosDebug.set(tempFramePoint1);
         activeSupportFootTrajectory.getFrameVelocity(tempFrameVector);
         supportFootVelDebug.set(tempFrameVector);
         activeSupportFootTrajectory.getFrameAcceleration(tempFrameVector);
         supportFootAccDebug.set(tempFrameVector);
      }
   }

   @Override
   public List<AngularMomentumTrajectory> getTransferAngularMomentumTrajectories()
   {
      return transferAngularMomentumTrajectories;
   }

   @Override
   public List<AngularMomentumTrajectory> getSwingAngularMomentumTrajectories()
   {
      return swingAngularMomentumTrajectories; //null
   }

   private class TrajectoryDebug extends YoSegmentedFrameTrajectory3D
   {
      public TrajectoryDebug(String name, int maxNumberOfSegments, int maxNumberOfCoefficients, YoRegistry registry)
      {
         super(name, maxNumberOfSegments, maxNumberOfCoefficients, registry);
      }

      public void set(FrameTrajectory3D trajToCopy)
      {
         segments.get(getNumberOfSegments()).set(trajToCopy);
         numberOfSegments.increment();
      }
   }

   private boolean calculateAngularMomentumForPhase(WalkingTrajectoryType walkingTrajectoryType)
   {
      if(walkingTrajectoryType.equals(WalkingTrajectoryType.SWING))
      {
         return planSwingAngularMomentum.getBooleanValue();
      }
      else
      {
         return planTransferAngularMomentum.getBooleanValue();
      }
   }
}
