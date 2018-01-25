package us.ihmc.robotics.parameterGui.tuning;

public class DoubleSpinner extends NumericSpinner<Double>
{
   public DoubleSpinner()
   {
      super(new DoubleSpinnerValueFactory(0.1));
   }

   @Override
   public void setMaxValue(Double maxValue)
   {
      DoubleSpinnerValueFactory valueFactory = (DoubleSpinnerValueFactory) getValueFactory();
      valueFactory.setMax(maxValue);
      revalidate();
   }

   @Override
   public void setMinValue(Double minValue)
   {
      DoubleSpinnerValueFactory valueFactory = (DoubleSpinnerValueFactory) getValueFactory();
      valueFactory.setMin(minValue);
      revalidate();
   }

   @Override
   public Double convertStringToNumber(String numberString)
   {
      if (numberString.endsWith("e") || numberString.endsWith("E"))
      {
         return Double.parseDouble(numberString.substring(0, numberString.length() - 1));
      }
      return Double.parseDouble(numberString);
   }

   @Override
   public String convertNumberToString(Double number)
   {
      return Double.toString(number);
   }

   @Override
   public String[] getSpecialStringOptions()
   {
      return new String[] {convertNumberToString(Double.POSITIVE_INFINITY), convertNumberToString(Double.NEGATIVE_INFINITY)};
   }
}
