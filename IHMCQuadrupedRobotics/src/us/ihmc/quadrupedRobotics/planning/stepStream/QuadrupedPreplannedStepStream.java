package us.ihmc.quadrupedRobotics.planning.stepStream;

import us.ihmc.quadrupedRobotics.estimator.referenceFrames.QuadrupedReferenceFrames;
import us.ihmc.quadrupedRobotics.planning.QuadrupedTimedStep;
import us.ihmc.quadrupedRobotics.providers.QuadrupedTimedStepInputProvider;
import us.ihmc.quadrupedRobotics.util.PreallocatedDeque;
import us.ihmc.quadrupedRobotics.util.PreallocatedDequeSorter;
import us.ihmc.robotics.dataStructures.variable.DoubleYoVariable;
import us.ihmc.robotics.geometry.FrameOrientation;

import java.util.ArrayList;
import java.util.Comparator;

public class QuadrupedPreplannedStepStream implements QuadrupedStepStream
{
   private static int MAXIMUM_STEP_QUEUE_SIZE = 100;

   private final QuadrupedTimedStepInputProvider timedStepInputProvider;
   private final QuadrupedReferenceFrames referenceFrames;
   private final DoubleYoVariable timestamp;
   private final PreallocatedDeque<QuadrupedTimedStep> stepDeque;
   private final FrameOrientation bodyOrientation;

   public QuadrupedPreplannedStepStream(QuadrupedTimedStepInputProvider timedStepInputProvider, QuadrupedReferenceFrames referenceFrames, DoubleYoVariable timestamp)
   {
      this.timedStepInputProvider = timedStepInputProvider;
      this.referenceFrames = referenceFrames;
      this.timestamp = timestamp;
      this.stepDeque = new PreallocatedDeque<>(QuadrupedTimedStep.class, MAXIMUM_STEP_QUEUE_SIZE);
      this.bodyOrientation = new FrameOrientation();
   }

   private Comparator<QuadrupedTimedStep> compareByEndTime = new Comparator<QuadrupedTimedStep>()
   {
      @Override
      public int compare(QuadrupedTimedStep a, QuadrupedTimedStep b)
      {
         return Double.compare(a.getTimeInterval().getEndTime(), b.getTimeInterval().getEndTime());
      }
   };

   @Override
   public void onEntry()
   {
      double currentTime = timestamp.getDoubleValue();
      ArrayList<QuadrupedTimedStep> steps = timedStepInputProvider.get();
      stepDeque.clear();
      for (int i = 0; i < steps.size(); i++)
      {
         double timeShift = steps.get(i).isAbsolute() ? 0.0 : currentTime;
         double touchdownTime = steps.get(i).getTimeInterval().getEndTime();
         if (touchdownTime + timeShift >= currentTime)
         {
            stepDeque.pushBack();
            stepDeque.back().set(steps.get(i));
            stepDeque.back().getTimeInterval().shiftInterval(timeShift);
            stepDeque.back().setAbsolute(true);
         }
      }
      PreallocatedDequeSorter.sort(stepDeque, compareByEndTime);

      bodyOrientation.setToZero(referenceFrames.getCenterOfFeetZUpFrameAveragingLowestZHeightsAcrossEnds());
   }

   @Override
   public void process()
   {
      // dequeue completed steps
      double currentTime = timestamp.getDoubleValue();
      while ((stepDeque.size() > 0) && (currentTime > stepDeque.front().getTimeInterval().getEndTime()))
      {
         stepDeque.popFront();
      }

      bodyOrientation.setToZero(referenceFrames.getCenterOfFeetZUpFrameAveragingLowestZHeightsAcrossEnds());
   }

   @Override
   public void onExit()
   {

   }

   @Override
   public void getBodyOrientation(FrameOrientation bodyOrientation)
   {
      bodyOrientation.setIncludingFrame(this.bodyOrientation);
   }

   @Override
   public PreallocatedDeque<QuadrupedTimedStep> getSteps()
   {
      return stepDeque;
   }
}
