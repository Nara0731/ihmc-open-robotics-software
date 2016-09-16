package us.ihmc.commonWalkingControlModules.instantaneousCapturePoint.icpOptimization;

import us.ihmc.commonWalkingControlModules.bipedSupportPolygons.BipedSupportPolygons;
import us.ihmc.commonWalkingControlModules.configurations.CapturePointPlannerParameters;
import us.ihmc.commonWalkingControlModules.instantaneousCapturePoint.smoothICPGenerator.CapturePointTools;
import us.ihmc.humanoidRobotics.bipedSupportPolygons.ContactablePlaneBody;
import us.ihmc.humanoidRobotics.footstep.Footstep;
import us.ihmc.robotics.MathTools;
import us.ihmc.robotics.dataStructures.registry.YoVariableRegistry;
import us.ihmc.robotics.dataStructures.variable.BooleanYoVariable;
import us.ihmc.robotics.dataStructures.variable.DoubleYoVariable;
import us.ihmc.robotics.dataStructures.variable.EnumYoVariable;
import us.ihmc.robotics.dataStructures.variable.IntegerYoVariable;
import us.ihmc.robotics.geometry.*;
import us.ihmc.robotics.math.frames.YoFramePoint2d;
import us.ihmc.robotics.math.frames.YoFrameVector2d;
import us.ihmc.robotics.referenceFrames.ReferenceFrame;
import us.ihmc.robotics.robotSide.RobotSide;
import us.ihmc.robotics.robotSide.SideDependentList;
import us.ihmc.simulationconstructionset.yoUtilities.graphics.YoGraphicsListRegistry;

import javax.vecmath.Matrix3d;
import javax.vecmath.Vector3d;
import java.util.ArrayList;

public class ICPOptimizationController
{
   private static final boolean referenceFromNewCMP = true;

   private static final String yoNamePrefix = "controller";
   private static final ReferenceFrame worldFrame = ReferenceFrame.getWorldFrame();

   private final YoVariableRegistry registry = new YoVariableRegistry(getClass().getSimpleName());

   private final IntegerYoVariable numberOfFootstepsToConsider = new IntegerYoVariable("numberOfFootstepsToConsider", registry);

   private final BooleanYoVariable useTwoCMPsInControl = new BooleanYoVariable("useTwoCMPsInControl", registry);
   private final BooleanYoVariable useInitialICP = new BooleanYoVariable("useInitialICP", registry);
   private final BooleanYoVariable useFeedback = new BooleanYoVariable("useFeedback", registry);
   private final BooleanYoVariable useStepAdjustment = new BooleanYoVariable("useStepAdjustment", registry);
   private final BooleanYoVariable useFootstepRegularization = new BooleanYoVariable("useFootstepRegularization", registry);
   private final BooleanYoVariable useFeedbackRegularization = new BooleanYoVariable("useFeedbackRegularization", registry);
   private final BooleanYoVariable useFeedbackWeightHardening = new BooleanYoVariable("useFeedbackWeightHardening", registry);

   private final BooleanYoVariable scaleStepRegularizationWeightWithTime = new BooleanYoVariable("scaleStepRegularizationWeightWithTime", registry);
   private final BooleanYoVariable scaleFeedbackWeightWithGain = new BooleanYoVariable("scaleFeedbackWeightWithGain", registry);
   private final BooleanYoVariable scaleUpcomingStepWeights = new BooleanYoVariable("scaleUpcomingStepWeights", registry);

   private final BooleanYoVariable isStanding = new BooleanYoVariable(yoNamePrefix + "IsStanding", registry);
   private final BooleanYoVariable isInTransfer = new BooleanYoVariable(yoNamePrefix + "IsInTransfer", registry);
   private final BooleanYoVariable isInitialTransfer = new BooleanYoVariable(yoNamePrefix + "IsInitialTransfer", registry);

   private final DoubleYoVariable doubleSupportDuration = new DoubleYoVariable(yoNamePrefix + "DoubleSupportDuration", registry);
   private final DoubleYoVariable singleSupportDuration = new DoubleYoVariable(yoNamePrefix + "SingleSupportDuration", registry);
   private final DoubleYoVariable initialDoubleSupportDuration = new DoubleYoVariable(yoNamePrefix + "InitialTransferDuration", registry);

   private final EnumYoVariable<RobotSide> transferToSide = new EnumYoVariable<>(yoNamePrefix + "TransferToSide", registry, RobotSide.class, true);
   private final EnumYoVariable<RobotSide> supportSide = new EnumYoVariable<>(yoNamePrefix + "SupportSide", registry, RobotSide.class, true);

   private final DoubleYoVariable initialTime = new DoubleYoVariable("initialTime", registry);
   private final DoubleYoVariable timeInCurrentState = new DoubleYoVariable("timeInCurrentState", registry);
   private final DoubleYoVariable timeRemainingInState = new DoubleYoVariable("timeRemainingInState", registry);

   private final FramePoint2d currentICP = new FramePoint2d();
   private final FramePoint2d desiredICP = new FramePoint2d();
   private final FrameVector2d desiredICPVelocity = new FrameVector2d();
   private final FramePoint2d perfectCMP = new FramePoint2d();

   private final YoFramePoint2d controllerFeedbackCMP = new YoFramePoint2d("controllerFeedbackCMP", worldFrame, registry);
   private final YoFrameVector2d controllerFeedbackCMPDelta = new YoFrameVector2d("controllerFeedbackCMPDelta", worldFrame, registry);

   private final FramePoint2d finalICPRecursion = new FramePoint2d();
   private final FramePoint2d cmpOffsetRecursionEffect = new FramePoint2d();
   private final FramePoint2d stanceCMPProjection = new FramePoint2d();
   private final FramePoint2d beginningOfStateICPProjection = new FramePoint2d();

   private final YoFramePoint2d beginningOfStateICP = new YoFramePoint2d("beginningOfStateICP", worldFrame, registry);

   private final ArrayList<Footstep> upcomingFootsteps = new ArrayList<>();
   private final ArrayList<YoFramePoint2d> upcomingFootstepLocations = new ArrayList<>();
   private final ArrayList<YoFramePoint2d> footstepSolutions = new ArrayList<>();

   private final DoubleYoVariable footstepWeight = new DoubleYoVariable("footstepWeight", registry);
   private final DoubleYoVariable footstepRegularizationWeight = new DoubleYoVariable("footstepRegularizationWeight", registry);
   private final DoubleYoVariable feedbackWeight = new DoubleYoVariable("feedbackWeight", registry);
   private final DoubleYoVariable feedbackRegularizationWeight = new DoubleYoVariable("feedbackRegularizationWeight", registry);
   private final DoubleYoVariable scaledFootstepRegularizationWeight = new DoubleYoVariable("scaledFootstepRegularizationWeight", registry);
   private final YoFramePoint2d scaledFeedbackWeight = new YoFramePoint2d("scaledFeedbackWeight", worldFrame, registry);
   private final DoubleYoVariable dynamicRelaxationWeight = new DoubleYoVariable("dynamicRelaxationWeight", registry);

   private final DoubleYoVariable feedbackOrthogonalGain = new DoubleYoVariable("feedbackOrthogonalGain", registry);
   private final DoubleYoVariable feedbackParallelGain = new DoubleYoVariable("feedbackParallelGain", registry);

   private final IntegerYoVariable numberOfIterations = new IntegerYoVariable("icpOptimizationNumberOfIterations", registry);

   private final ICPOptimizationSolver solver;
   private final FootstepRecursionMultiplierCalculator footstepRecursionMultiplierCalculator;

   private final ICPOptimizationParameters icpOptimizationParameters;

   private final ICPOptimizationCMPConstraintHandler cmpConstraintHandler;
   private final ICPOptimizationSolutionHandler solutionHandler;
   private final ICPOptimizationInputHandler inputHandler;

   private final int maximumNumberOfFootstepsToConsider;

   private boolean localUseTwoCMPs;
   private boolean localUseInitialICP;
   private boolean localUseFeedback;
   private boolean localUseFeedbackRegularization;
   private boolean localUseFeedbackWeightHardening;
   private boolean localUseStepAdjustment;
   private boolean localUseFootstepRegularization;

   private boolean localScaleUpcomingStepWeights;


   // todo disable footstep adjustment in end of single support

   private final Vector2dZUpFrame icpVelocityDirectionFrame;

   public ICPOptimizationController(CapturePointPlannerParameters icpPlannerParameters, ICPOptimizationParameters icpOptimizationParameters,
         BipedSupportPolygons bipedSupportPolygons, SideDependentList<? extends ContactablePlaneBody> contactableFeet, YoVariableRegistry parentRegistry,
         YoGraphicsListRegistry yoGraphicsListRegistry)
   {
      this.icpOptimizationParameters = icpOptimizationParameters;

      maximumNumberOfFootstepsToConsider = icpOptimizationParameters.getMaximumNumberOfFootstepsToConsider();
      numberOfFootstepsToConsider.set(icpOptimizationParameters.numberOfFootstepsToConsider());

      initialDoubleSupportDuration.set(icpPlannerParameters.getDoubleSupportInitialTransferDuration());

      int totalVertices = 0;
      for (RobotSide robotSide : RobotSide.values)
         totalVertices += contactableFeet.get(robotSide).getTotalNumberOfContactPoints();

      solver = new ICPOptimizationSolver(icpOptimizationParameters, totalVertices, registry);

      useTwoCMPsInControl.set(icpPlannerParameters.useTwoCMPsPerSupport());
      useInitialICP.set(true); // todo
      useFeedback.set(icpOptimizationParameters.useFeedback());
      useStepAdjustment.set(icpOptimizationParameters.useStepAdjustment());
      useFootstepRegularization.set(icpOptimizationParameters.useFootstepRegularization());
      useFeedbackRegularization.set(icpOptimizationParameters.useFeedbackRegularization());
      useFeedbackWeightHardening.set(icpOptimizationParameters.useFeedbackWeightHardening());

      scaleStepRegularizationWeightWithTime.set(icpOptimizationParameters.scaleStepRegularizationWeightWithTime());
      scaleFeedbackWeightWithGain.set(icpOptimizationParameters.scaleFeedbackWeightWithGain());
      scaleUpcomingStepWeights.set(icpOptimizationParameters.scaleUpcomingStepWeights());

      // todo set the regularization as a function of control dt
      footstepWeight.set(icpOptimizationParameters.getFootstepWeight());
      footstepRegularizationWeight.set(icpOptimizationParameters.getFootstepRegularizationWeight());
      feedbackWeight.set(icpOptimizationParameters.getFeedbackWeight());
      feedbackRegularizationWeight.set(icpOptimizationParameters.getFeedbackRegularizationWeight());
      feedbackOrthogonalGain.set(icpOptimizationParameters.getFeedbackOrthogonalGain());
      feedbackParallelGain.set(icpOptimizationParameters.getFeedbackParallelGain());
      dynamicRelaxationWeight.set(icpOptimizationParameters.getDynamicRelaxationWeight());


      DoubleYoVariable exitCMPDurationInPercentOfStepTime = new DoubleYoVariable(yoNamePrefix + "TimeSpentOnExitCMPInPercentOfStepTime", registry);
      DoubleYoVariable doubleSupportSplitFraction = new DoubleYoVariable(yoNamePrefix + "DoubleSupportSplitFraction", registry);

      exitCMPDurationInPercentOfStepTime.set(icpPlannerParameters.getTimeSpentOnExitCMPInPercentOfStepTime());
      doubleSupportSplitFraction.set(icpPlannerParameters.getDoubleSupportSplitFraction());

      footstepRecursionMultiplierCalculator = new FootstepRecursionMultiplierCalculator(icpPlannerParameters, exitCMPDurationInPercentOfStepTime,
            doubleSupportSplitFraction, maximumNumberOfFootstepsToConsider, registry);

      cmpConstraintHandler = new ICPOptimizationCMPConstraintHandler(bipedSupportPolygons, icpOptimizationParameters, registry);
      solutionHandler = new ICPOptimizationSolutionHandler(icpOptimizationParameters, footstepRecursionMultiplierCalculator, registry, yoGraphicsListRegistry);
      inputHandler = new ICPOptimizationInputHandler(icpPlannerParameters, bipedSupportPolygons, contactableFeet, maximumNumberOfFootstepsToConsider,
            footstepRecursionMultiplierCalculator, doubleSupportDuration, singleSupportDuration, exitCMPDurationInPercentOfStepTime, doubleSupportSplitFraction,
            registry, yoGraphicsListRegistry);

      for (int i = 0; i < maximumNumberOfFootstepsToConsider; i++)
      {
         upcomingFootstepLocations.add(new YoFramePoint2d("upcomingFootstepLocation" + i, worldFrame, registry));
         footstepSolutions.add(new YoFramePoint2d("footstepSolutionLocation" + i, worldFrame, registry));
      }

      icpVelocityDirectionFrame = new Vector2dZUpFrame("icpVelocityDirectionFrame", worldFrame);

      parentRegistry.addChild(registry);
   }

   public void setStepDurations(double doubleSupportDuration, double singleSupportDuration)
   {
      setDoubleSupportDuration(doubleSupportDuration);
      setSingleSupportDuration(singleSupportDuration);
   }

   public void setDoubleSupportDuration(double doubleSupportDuration)
   {
      this.doubleSupportDuration.set(doubleSupportDuration);
   }

   public void setSingleSupportDuration(double singleSupportDuration)
   {
      this.singleSupportDuration.set(singleSupportDuration);
   }

   public void clearPlan()
   {
      upcomingFootsteps.clear();
      footstepRecursionMultiplierCalculator.reset();
      inputHandler.clearPlan();
      for (int i = 0; i < maximumNumberOfFootstepsToConsider; i++)
         upcomingFootstepLocations.get(i).setToZero();
   }

   private final FramePoint2d tmpFramePoint2d = new FramePoint2d();
   public void addFootstepToPlan(Footstep footstep)
   {
      if (footstep != null)
      {
         upcomingFootsteps.add(footstep);
         footstep.getPosition2d(tmpFramePoint2d);
         upcomingFootstepLocations.get(upcomingFootsteps.size() - 1).set(tmpFramePoint2d);
         inputHandler.addFootstepToPlan(footstep);

         footstepSolutions.get(upcomingFootsteps.size() - 1).set(tmpFramePoint2d);
      }
   }

   public void initializeForStanding(double initialTime)
   {
      this.initialTime.set(initialTime);
      isStanding.set(true);
      isInTransfer.set(false);
      isInitialTransfer.set(true);

      setProblemBooleans();

      footstepRecursionMultiplierCalculator.resetTimes();

      cmpConstraintHandler.initializeCMPConstraintForDoubleSupport(solver);
   }

   public void initializeForTransfer(double initialTime, RobotSide transferToSide, double omega0)
   {
      this.initialTime.set(initialTime);
      this.transferToSide.set(transferToSide);
      if (transferToSide == null)
         transferToSide = RobotSide.LEFT;
      isInTransfer.set(true);

      beginningOfStateICP.set(solutionHandler.getControllerReferenceICP());

      int numberOfFootstepsToConsider = clipNumberOfFootstepsToConsiderToProblem(this.numberOfFootstepsToConsider.getIntegerValue());

      setProblemBooleans();

      footstepRecursionMultiplierCalculator.resetTimes();
      if (isInitialTransfer.getBooleanValue())
         footstepRecursionMultiplierCalculator.submitTimes(0, initialDoubleSupportDuration.getDoubleValue(), singleSupportDuration.getDoubleValue());
      else
         footstepRecursionMultiplierCalculator.submitTimes(0, doubleSupportDuration.getDoubleValue(), singleSupportDuration.getDoubleValue());

      for (int i = 1; i < numberOfFootstepsToConsider + 1; i++)
         footstepRecursionMultiplierCalculator.submitTimes(i, doubleSupportDuration.getDoubleValue(), singleSupportDuration.getDoubleValue());

      footstepRecursionMultiplierCalculator.computeRecursionMultipliers(numberOfFootstepsToConsider, isInTransfer.getBooleanValue(),
            localUseTwoCMPs, omega0);

      inputHandler.initializeForDoubleSupport(isStanding.getBooleanValue(), localUseTwoCMPs, transferToSide, omega0);

      if (localUseFootstepRegularization)
         resetFootstepRegularizationTask();
      if (localUseFeedbackRegularization)
         solver.resetFeedbackRegularization();

      cmpConstraintHandler.initializeCMPConstraintForDoubleSupport(solver);
   }

   public void initializeForSingleSupport(double initialTime, RobotSide supportSide, double omega0)
   {
      this.initialTime.set(initialTime);
      this.supportSide.set(supportSide);
      isStanding.set(false);
      isInTransfer.set(false);
      isInitialTransfer.set(false);

      beginningOfStateICP.set(solutionHandler.getControllerReferenceICP());

      int numberOfFootstepsToConsider = clipNumberOfFootstepsToConsiderToProblem(this.numberOfFootstepsToConsider.getIntegerValue());

      setProblemBooleans();

      footstepRecursionMultiplierCalculator.resetTimes();
      footstepRecursionMultiplierCalculator.submitTimes(0, 0.0, singleSupportDuration.getDoubleValue());

      for (int i = 1; i < numberOfFootstepsToConsider + 1; i++)
         footstepRecursionMultiplierCalculator.submitTimes(i, doubleSupportDuration.getDoubleValue(), singleSupportDuration.getDoubleValue());
      footstepRecursionMultiplierCalculator.submitTimes(numberOfFootstepsToConsider + 1, doubleSupportDuration.getDoubleValue(), singleSupportDuration.getDoubleValue());

      footstepRecursionMultiplierCalculator.computeRecursionMultipliers(numberOfFootstepsToConsider, isInTransfer.getBooleanValue(),
            localUseTwoCMPs, omega0);

      inputHandler.initializeForSingleSupport(localUseTwoCMPs, supportSide, omega0);

      if (localUseFootstepRegularization)
         resetFootstepRegularizationTask();
      if (localUseFeedbackRegularization)
         solver.resetFeedbackRegularization();

      cmpConstraintHandler.initializeCMPConstraintForSingleSupport(supportSide, solver);
   }

   private void setProblemBooleans()
   {
      localUseTwoCMPs = useTwoCMPsInControl.getBooleanValue();
      localUseInitialICP = useInitialICP.getBooleanValue();
      localUseFeedback = useFeedback.getBooleanValue();
      localUseStepAdjustment = useStepAdjustment.getBooleanValue();
      localUseFootstepRegularization = useFootstepRegularization.getBooleanValue();
      localUseFeedbackRegularization = useFeedbackRegularization.getBooleanValue();
      localUseFeedbackWeightHardening = useFeedbackWeightHardening.getBooleanValue();

      localScaleUpcomingStepWeights = scaleUpcomingStepWeights.getBooleanValue();
   }

   private final FramePoint2d desiredCMP = new FramePoint2d();
   private final FrameVector2d desiredCMPDelta = new FrameVector2d();

   public void compute(double currentTime, FramePoint2d desiredICP, FrameVector2d desiredICPVelocity, FramePoint2d currentICP, double omega0)
   {
      desiredICP.changeFrame(worldFrame);
      desiredICPVelocity.changeFrame(worldFrame);
      currentICP.changeFrame(worldFrame);

      this.currentICP.set(currentICP);
      this.desiredICP.set(desiredICP);
      this.desiredICPVelocity.set(desiredICPVelocity);

      CapturePointTools.computeDesiredCentroidalMomentumPivot(desiredICP, desiredICPVelocity, omega0, perfectCMP);

      computeTimeInCurrentState(currentTime);
      computeTimeRemainingInState();

      scaleStepRegularizationWeightWithTime();
      scaleFeedbackWeightWithGain();

      if (isStanding.getBooleanValue())
         doFeedbackOnlyControl(omega0);
      else
         doControlForStepping(omega0);

      solver.getCMPFeedback(desiredCMP);
      solver.getCMPFeedbackDifference(desiredCMPDelta);

      if (referenceFromNewCMP)
      {
         solutionHandler.getControllerReferenceCMP(desiredCMP);
         desiredCMP.add(desiredCMPDelta);
      }

      controllerFeedbackCMP.set(desiredCMP);
      controllerFeedbackCMPDelta.set(desiredCMPDelta);

      solutionHandler.updateCostsToGo(solver);
   }

   private void doControlForStepping(double omega0)
   {
      int numberOfFootstepsToConsider = clipNumberOfFootstepsToConsiderToProblem(this.numberOfFootstepsToConsider.getIntegerValue());

      solver.submitProblemConditions(numberOfFootstepsToConsider, localUseStepAdjustment, localUseFeedback, localUseTwoCMPs); //, localUseActiveCMPOptimization);

      if (localUseFeedback)
      {
         setFeedbackConditions();

         if (localUseFeedbackWeightHardening)
            solver.setUseFeedbackWeightHardening();

         if (localUseFeedbackRegularization)
            solver.setFeedbackRegularizationWeight(feedbackRegularizationWeight.getDoubleValue());
      }

      if (localUseStepAdjustment && !isInTransfer.getBooleanValue())
      {
         for (int footstepIndex = 0; footstepIndex < numberOfFootstepsToConsider; footstepIndex++)
            submitFootstepConditionsToSolver(footstepIndex);

         if (localUseFootstepRegularization)
            solver.setFootstepRegularizationWeight(scaledFootstepRegularizationWeight.getDoubleValue());
      }

      inputHandler.computeFinalICPRecursion(finalICPRecursion, numberOfFootstepsToConsider, localUseTwoCMPs, isInTransfer.getBooleanValue(), omega0);
      inputHandler.computeStanceCMPProjection(stanceCMPProjection, timeRemainingInState.getDoubleValue(), localUseTwoCMPs, isInTransfer.getBooleanValue(),
            localUseInitialICP, omega0);
      inputHandler.computeBeginningOfStateICPProjection(beginningOfStateICPProjection, beginningOfStateICP.getFrameTuple2d());

      FramePoint2d offsetRecursionEffect = null;
      if (localUseTwoCMPs)
      {
         inputHandler.computeCMPOffsetRecursionEffect(cmpOffsetRecursionEffect, upcomingFootstepLocations, numberOfFootstepsToConsider);
         offsetRecursionEffect = cmpOffsetRecursionEffect;
      }

      solver.compute(finalICPRecursion, offsetRecursionEffect, currentICP, perfectCMP, stanceCMPProjection, beginningOfStateICPProjection);

      numberOfIterations.set(solver.getNumberOfIterations());

      solutionHandler.extractFootstepSolutions(footstepSolutions, upcomingFootsteps, numberOfFootstepsToConsider,solver);

      solutionHandler.computeReferenceFromSolutions(footstepSolutions, inputHandler, beginningOfStateICP, omega0, numberOfFootstepsToConsider);
      solutionHandler.computeNominalValues(upcomingFootstepLocations, inputHandler, beginningOfStateICP, omega0, numberOfFootstepsToConsider);
   }

   private final FramePoint2d feedbackGains = new FramePoint2d();
   private void setFeedbackConditions()
   {
      getTransformedFeedbackGains(feedbackGains);
      solver.setFeedbackConditions(scaledFeedbackWeight.getX(), scaledFeedbackWeight.getY(), feedbackGains.getX(), feedbackGains.getY(), dynamicRelaxationWeight.getDoubleValue());
   }

   private void getTransformedFeedbackGains(FramePoint2d feedbackGainsToPack)
   {
      double epsilonZeroICPVelocity = 1e-5;

      if (desiredICPVelocity.lengthSquared() > MathTools.square(epsilonZeroICPVelocity))
      {
         icpVelocityDirectionFrame.setXAxis(desiredICPVelocity);
         feedbackGainsToPack.setToZero(icpVelocityDirectionFrame);

         feedbackGainsToPack.setX(1.0 + feedbackParallelGain.getDoubleValue());
         feedbackGainsToPack.setY(1.0 + feedbackOrthogonalGain.getDoubleValue());

         feedbackGainsToPack.changeFrame(worldFrame);
         feedbackGainsToPack.set(Math.abs(feedbackGainsToPack.getX()), Math.abs(feedbackGainsToPack.getY()));
      }
      else
      {
         feedbackGainsToPack.setToZero(worldFrame);
         feedbackGainsToPack.set(feedbackOrthogonalGain.getDoubleValue(), feedbackOrthogonalGain.getDoubleValue());
      }
   }


   private final FramePoint2d blankFramePoint = new FramePoint2d(worldFrame);
   private void doFeedbackOnlyControl(double omega0)
   {
      solver.submitProblemConditions(0, false, true, false);

      getTransformedFeedbackGains(feedbackGains);

      solver.setFeedbackConditions(scaledFeedbackWeight.getX(), scaledFeedbackWeight.getY(), feedbackGains.getX(), feedbackGains.getY(),
            dynamicRelaxationWeight.getDoubleValue());

      solver.compute(desiredICP, null, currentICP, perfectCMP, blankFramePoint, blankFramePoint);

      solutionHandler.setValuesForFeedbackOnly(desiredICP, desiredICPVelocity, omega0);
   }

   private void resetFootstepRegularizationTask()
   {
      int numberOfFootstepsToConsider = clipNumberOfFootstepsToConsiderToProblem(this.numberOfFootstepsToConsider.getIntegerValue());

      for (int i = 0; i < numberOfFootstepsToConsider; i++)
         solver.resetFootstepRegularization(i, upcomingFootstepLocations.get(i).getFrameTuple2d());
   }

   private int clipNumberOfFootstepsToConsiderToProblem(int numberOfFootstepsToConsider)
   {
      numberOfFootstepsToConsider = Math.min(numberOfFootstepsToConsider, upcomingFootsteps.size());
      numberOfFootstepsToConsider = Math.min(numberOfFootstepsToConsider, maximumNumberOfFootstepsToConsider);

      if (!localUseStepAdjustment || isInTransfer.getBooleanValue())
         numberOfFootstepsToConsider = 0;

      return numberOfFootstepsToConsider;
   }

   private void submitFootstepConditionsToSolver(int footstepIndex)
   {
      double footstepWeight = this.footstepWeight.getDoubleValue();

      if (localScaleUpcomingStepWeights)
         footstepWeight = footstepWeight / (footstepIndex + 1);

      double footstepRecursionMultiplier;
      if (localUseTwoCMPs)
      {
         double entryMutliplier = footstepRecursionMultiplierCalculator.getCMPRecursionEntryMultiplier(footstepIndex);
         double exitMutliplier = footstepRecursionMultiplierCalculator.getCMPRecursionExitMultiplier(footstepIndex);

         footstepRecursionMultiplier = entryMutliplier + exitMutliplier;
      }
      else
      {
         footstepRecursionMultiplier = footstepRecursionMultiplierCalculator.getCMPRecursionExitMultiplier(footstepIndex);
      }
      footstepRecursionMultiplier *= footstepRecursionMultiplierCalculator.getCurrentStateProjectionMultiplier();

      solver.setFootstepAdjustmentConditions(footstepIndex, footstepRecursionMultiplier, footstepWeight,
            upcomingFootstepLocations.get(footstepIndex).getFrameTuple2d());
   }


   private void computeTimeInCurrentState(double currentTime)
   {
      timeInCurrentState.set(currentTime - initialTime.getDoubleValue());
   }

   private void computeTimeRemainingInState()
   {
      if (isStanding.getBooleanValue())
      {
         timeRemainingInState.set(0.0);
      }
      else
      {
         double remainingTime;
         if (isInTransfer.getBooleanValue())
         {
            remainingTime = doubleSupportDuration.getDoubleValue() - timeInCurrentState.getDoubleValue();
         }
         else
         {
            remainingTime = singleSupportDuration.getDoubleValue() - timeInCurrentState.getDoubleValue();
         }

         remainingTime = Math.max(icpOptimizationParameters.getMinimumTimeRemaining(), remainingTime);
         timeRemainingInState.set(remainingTime);
      }
   }

   private void scaleStepRegularizationWeightWithTime()
   {
      if (scaleStepRegularizationWeightWithTime.getBooleanValue())
      {
         double alpha = timeRemainingInState.getDoubleValue() / singleSupportDuration.getDoubleValue();
         scaledFootstepRegularizationWeight.set(footstepRegularizationWeight.getDoubleValue() / alpha);
      }
      else
      {
         scaledFootstepRegularizationWeight.set(footstepRegularizationWeight.getDoubleValue());
      }
   }

   private void scaleFeedbackWeightWithGain()
   {
      if (scaleFeedbackWeightWithGain.getBooleanValue())
      {
         getTransformedFeedbackGains(feedbackGains);

         double alpha = Math.sqrt(Math.pow(feedbackGains.getX(), 2) + Math.pow(feedbackGains.getY(), 2));
         scaledFeedbackWeight.set(feedbackWeight.getDoubleValue() / alpha, feedbackWeight.getDoubleValue() / alpha);
      }
      else
      {
         scaledFeedbackWeight.set(feedbackWeight.getDoubleValue(), feedbackWeight.getDoubleValue());
      }
   }

   public int getNumberOfFootstepsToConsider()
   {
      return numberOfFootstepsToConsider.getIntegerValue();
   }

   public void getDesiredCMP(FramePoint2d desiredCMPToPack)
   {
      controllerFeedbackCMP.getFrameTuple2d(desiredCMPToPack);
   }

   public void getFootstepSolution(int footstepIndex, FramePoint2d footstepSolutionToPack)
   {
      footstepSolutions.get(footstepIndex).getFrameTuple2d(footstepSolutionToPack);
   }

   public boolean wasFootstepAdjusted()
   {
      return solutionHandler.wasFootstepAdjusted();
   }

   private class Vector2dZUpFrame extends ReferenceFrame
   {
      private static final long serialVersionUID = -1810366869361449743L;
      private final FrameVector2d xAxis;
      private final Vector3d x = new Vector3d();
      private final Vector3d y = new Vector3d();
      private final Vector3d z = new Vector3d();
      private final Matrix3d rotation = new Matrix3d();

      public Vector2dZUpFrame(String string, ReferenceFrame parentFrame)
      {
         super(string, parentFrame);
         xAxis = new FrameVector2d(parentFrame);
      }

      public void setXAxis(FrameVector2d xAxis)
      {
         this.xAxis.setIncludingFrame(xAxis);
         this.xAxis.changeFrame(parentFrame);
         this.xAxis.normalize();
         update();
      }

      @Override
      protected void updateTransformToParent(RigidBodyTransform transformToParent)
      {
         x.set(xAxis.getX(), xAxis.getY(), 0.0);
         z.set(0.0, 0.0, 1.0);
         y.cross(z, x);

         rotation.setColumn(0, x);
         rotation.setColumn(1, y);
         rotation.setColumn(2, z);

         transformToParent.setRotationAndZeroTranslation(rotation);
      }
   }
}
