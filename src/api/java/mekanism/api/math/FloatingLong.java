package mekanism.api.math;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import mcp.MethodsReturnNonnullByDefault;
import mekanism.api.NBTConstants;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.common.util.INBTSerializable;

/**
 * A class representing a value defined by a long, and a floating point number stored in a short
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class FloatingLong extends Number implements Comparable<FloatingLong>, INBTSerializable<CompoundNBT> {

    //TODO: Implement this class, and improve java docs. Organize and move all static methods either to the top or the bottom
    private static final int DECIMAL_DIGITS = 4;//We only can support 4 digits in our decimal
    private static final short MAX_DECIMAL = 9_999;
    private static final short SINGLE_UNIT = MAX_DECIMAL + 1;
    public static final FloatingLong ZERO = createConst(0);
    public static final FloatingLong ONE = createConst(1);
    public static final FloatingLong MAX_VALUE = createConst(Long.MAX_VALUE, MAX_DECIMAL);

    public static FloatingLong getNewZero() {
        return create(0);
    }

    public static FloatingLong create(double value) {
        //TODO: Try to optimize/improve this at the very least it rounds incorrectly
        long lValue = (long) value;
        short decimal = parseDecimal(Double.toString(value));
        return create(lValue, decimal);
    }

    public static FloatingLong create(long value) {
        return create(value, (short) 0);
    }

    public static FloatingLong create(long value, short decimal) {
        return new FloatingLong(value, decimal, false);
    }

    public static FloatingLong createConst(double value) {
        //TODO: Try to optimize/improve this at the very least it rounds incorrectly
        long lValue = (long) value;
        short decimal = parseDecimal(Double.toString(value));
        return create(lValue, decimal);
    }

    public static FloatingLong createConst(long value) {
        return createConst(value, (short) 0);
    }

    public static FloatingLong createConst(long value, short decimal) {
        return new FloatingLong(value, decimal, true);
    }

    private long value;
    private short decimal;
    private final boolean isConstant;

    private FloatingLong(long value, short decimal, boolean isConstant) {
        this.isConstant = isConstant;
        setAndClampValues(value, decimal);
    }

    public long getValue() {
        return value;
    }

    public short getDecimal() {
        return decimal;
    }

    private void checkCanModify() {
        if (isConstant) {
            throw new IllegalStateException("Tried to modify a floating constant long");
        }
    }

    //DO NOT CALL THIS IF YOU ARE A CONSTANT
    private void setAndClampValues(long value, short decimal) {
        if (value < 0) {
            //TODO: Remove this clamp for value and allow it to be an unsigned long, and convert string parsing and creation
            value = 0;
        }
        if (decimal < 0) {
            decimal = 0;
        } else if (decimal > MAX_DECIMAL) {
            decimal = MAX_DECIMAL;
        }
        this.value = value;
        this.decimal = decimal;
    }

    public boolean isEmpty() {
        return value <= 0 && decimal <= 0;
    }

    public FloatingLong copy() {
        return new FloatingLong(value, decimal, false);
    }

    //TODO: Define a way of doing a set of operations all at once, and outputting a new value
    // given that way we can internally do all the calculations using primitives rather than spamming a lot of objects
    public void plusEqual(FloatingLong toAdd) {
        checkCanModify();
        long newValue;
        short newDecimal;
        try {
            newValue = Math.addExact(value, toAdd.value);
            newDecimal = (short) (decimal + toAdd.decimal);
            if (newDecimal > MAX_DECIMAL) {
                newDecimal -= SINGLE_UNIT;
                newValue = Math.addExact(newValue, 1); //could overflow here too, same exception will be thrown
            }
        } catch (ArithmeticException e) {
            newValue = MAX_VALUE.value;
            newDecimal = MAX_VALUE.decimal;
        }
        setAndClampValues(newValue, newDecimal);
    }

    public void minusEqual(FloatingLong toSubtract) {
        checkCanModify();
        //TODO: Handle it going negative
        long newValue = value - toSubtract.value;
        short newDecimal = (short) (decimal - toSubtract.decimal);
        if (newDecimal < 0) {
            newDecimal += SINGLE_UNIT;
            newValue--;
        }
        setAndClampValues(newValue, newDecimal);
    }

    public void timesEqual(FloatingLong toMultiply) {
        checkCanModify();
        //(a+b)*(c+d) where numbers represent decimal, numbers represent value
        FloatingLong temp = create(multiplyLongs(value, toMultiply.value));//a * c
        temp.plusEqual(multiplyLongAndDecimal(value, toMultiply.decimal));//a * d
        temp.plusEqual(multiplyLongAndDecimal(toMultiply.value, decimal));//b * c
        temp.plusEqual(multiplyDecimals(decimal, toMultiply.decimal));//b * d
        setAndClampValues(temp.value, temp.decimal);
    }

    public void divideEquals(FloatingLong toDivide) {
        checkCanModify();
        //TODO: Validate toDivide is not zero
        //TODO: Make a more direct implementation that doesn't go through big decimal
        BigDecimal divide = new BigDecimal(toString()).divide(new BigDecimal(toDivide.toString()), DECIMAL_DIGITS, RoundingMode.HALF_EVEN);
        long value = divide.longValue();
        short decimal = parseDecimal(divide.toString());
        setAndClampValues(value, decimal);
    }

    public FloatingLong add(FloatingLong toAdd) {
        FloatingLong toReturn = copy();
        toReturn.plusEqual(toAdd);
        return toReturn;
    }

    public FloatingLong add(long toAdd) {
        if (toAdd < 0) {
            throw new IllegalArgumentException("Addition called with negative number, this is not supported. FloatingLongs are always positive.");
        }
        return add(FloatingLong.create(toAdd));
    }

    public FloatingLong add(double toAdd) {
        if (toAdd < 0) {
            throw new IllegalArgumentException("Addition called with negative number, this is not supported. FloatingLongs are always positive.");
        }
        return add(FloatingLong.create(toAdd));
    }

    public FloatingLong subtract(FloatingLong toSubtract) {
        //TODO: NOTE: We probably need to look through this to make sure we don't accidentally go negative??
        // Or was that just an edge case for how we did calculations for the induction matrix
        FloatingLong toReturn = copy();
        toReturn.minusEqual(toSubtract);
        return toReturn;
    }

    public FloatingLong subtract(long toSubtract) {
        if (toSubtract < 0) {
            throw new IllegalArgumentException("Subtraction called with negative number, this is not supported. FloatingLongs are always positive.");
        }
        return subtract(FloatingLong.create(toSubtract));
    }

    public FloatingLong subtract(double toSubtract) {
        if (toSubtract < 0) {
            throw new IllegalArgumentException("Subtraction called with negative number, this is not supported. FloatingLongs are always positive.");
        }
        return subtract(FloatingLong.create(toSubtract));
    }

    public FloatingLong multiply(FloatingLong toMultiply) {
        FloatingLong toReturn = copy();
        toReturn.timesEqual(toMultiply);
        return toReturn;
    }

    public FloatingLong multiply(long toMultiply) {
        if (toMultiply < 0) {
            throw new IllegalArgumentException("Multiply called with negative number, this is not supported. FloatingLongs are always positive.");
        }
        return multiply(FloatingLong.create(toMultiply));
    }

    public FloatingLong multiply(double toMultiply) {
        if (toMultiply < 0) {
            throw new IllegalArgumentException("Multiply called with negative number, this is not supported. FloatingLongs are always positive.");
        }
        return multiply(FloatingLong.createConst(toMultiply));
    }

    public FloatingLong divide(FloatingLong toDivide) {
        FloatingLong toReturn = copy();
        toReturn.divideEquals(toDivide);
        return toReturn;
    }

    public FloatingLong divide(long toDivide) {
        if (toDivide < 0) {
            throw new IllegalArgumentException("Division called with negative number, this is not supported. FloatingLongs are always positive.");
        }
        return divide(FloatingLong.create(toDivide));
    }

    public FloatingLong divide(double toDivide) {
        if (toDivide < 0) {
            throw new IllegalArgumentException("Division called with negative number, this is not supported. FloatingLongs are always positive.");
        }
        return divide(FloatingLong.create(toDivide));
    }

    public double divideToLevel(FloatingLong toDivide) {
        //TODO: optimize out creating another object
        return toDivide.isEmpty() ? 1 : divide(toDivide).doubleValue();
    }

    //TODO: Note this doesn't create any new objects
    public FloatingLong max(FloatingLong other) {
        return smallerThan(other) ? other : this;
    }

    //TODO: Note this doesn't create any new objects
    public FloatingLong min(FloatingLong other) {
        return greaterThan(other) ? other : this;
    }

    private static long multiplyLongs(long d1, long d2) {
        try {
            //multiplyExact will throw an exception if we overflow
            return Math.multiplyExact(d1, d2);
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }

    private static FloatingLong multiplyLongAndDecimal(long d1, short d2) {
        //This can't overflow!
        if (d1 > Long.MAX_VALUE / SINGLE_UNIT) {
            return create((d1 / SINGLE_UNIT) * d2, (short) (d1 % SINGLE_UNIT * d2));
        }
        return create(d1 * d2 / SINGLE_UNIT, (short) (d1 * d2 % SINGLE_UNIT));
    }

    private static FloatingLong multiplyDecimals(short d1, short d2) {
        //If we wanted to round here, just get modulus and add if >= 0.5*SINGLE_UNIT
        long temp = (long) d1 * (long) d2 / SINGLE_UNIT;
        return create(0, (short) temp);
    }

    public boolean smallerThan(FloatingLong toCompare) {
        return compareTo(toCompare) < 0;
    }

    public boolean greaterThan(FloatingLong toCompare) {
        return compareTo(toCompare) > 0;
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote zero if equal to toCompare
     * <br>
     * less than zero if smaller than toCompare
     * <br>
     * greater than zero if bigger than toCompare
     * @implNote {@code 2} or {@code -2} if the overall value is different
     * <br>
     * {@code 1} or {@code -1} if the value is the same but the decimal is different
     */
    @Override
    public int compareTo(FloatingLong toCompare) {
        if (value < toCompare.value) {
            //If our primary value is smaller than toCompare's value we are always less than
            return -2;
        } else if (value > toCompare.value) {
            //If our primary value is bigger than toCompare's value we are always greater than
            return 2;
        }
        //Primary value is equal, check the decimal
        if (decimal < toCompare.decimal) {
            //If our primary value is equal, but our decimal smaller than toCompare's we are less than
            return -1;
        } else if (decimal > toCompare.decimal) {
            //If our primary value is equal, but our decimal bigger than toCompare's we are greater than
            return 1;
        }
        //Else we are equal
        return 0;
    }

    public boolean equals(FloatingLong other) {
        return value == other.value && decimal == other.decimal;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof FloatingLong && equals((FloatingLong) other);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, decimal);
    }

    @Override
    public int intValue() {
        return MathUtils.clampToInt(value);
    }

    @Override
    public long longValue() {
        return value;
    }

    @Override
    public float floatValue() {
        return intValue() + decimal / (float) SINGLE_UNIT;
    }

    @Override
    public double doubleValue() {
        return longValue() + decimal / (double) SINGLE_UNIT;
    }

    @Override
    public CompoundNBT serializeNBT() {
        CompoundNBT nbt = new CompoundNBT();
        nbt.putLong(NBTConstants.VALUE, value);
        nbt.putShort(NBTConstants.DECIMAL, decimal);
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundNBT nbt) {
        checkCanModify();
        setAndClampValues(nbt.getLong(NBTConstants.VALUE), nbt.getShort(NBTConstants.DECIMAL));
    }

    public void writeToBuffer(PacketBuffer buffer) {
        buffer.writeVarLong(value);
        buffer.writeShort(decimal);
    }

    @Override
    public String toString() {
        return toString(DECIMAL_DIGITS);
    }

    public String toString(int decimalPlaces) {
        if (decimal == 0) {
            return Long.toString(value);
        }
        if (decimalPlaces > DECIMAL_DIGITS) {
            decimalPlaces = DECIMAL_DIGITS;
        }
        String valueAsString = value + ".";
        String decimalAsString = Short.toString(decimal);
        int numberDigits = decimalAsString.length();
        if (numberDigits > decimalPlaces) {
            //We need to trim it
            decimalAsString = decimalAsString.substring(0, decimalPlaces);
        } else if (numberDigits < decimalPlaces) {
            //We need to prepend some zeros
            decimalAsString = getZeros(decimalPlaces - numberDigits) + decimalAsString;
        }
        return valueAsString + decimalAsString;
    }

    /**
     * Parses the string argument as a signed decimal {@link FloatingLong}. The characters in the string must all be decimal digits, with a decimal point being valid to
     * convey where the decimal starts.
     *
     * @param string a {@code String} containing the {@link FloatingLong} representation to be parsed
     *
     * @return the {@link FloatingLong} represented by the argument in decimal.
     *
     * @throws NumberFormatException if the string does not contain a parsable {@link FloatingLong}.
     */
    public static FloatingLong parseFloatingLong(String string) {
        long value;
        int index = string.indexOf(".");
        if (index == -1) {
            value = Long.parseLong(string);
        } else {
            value = Long.parseLong(string.substring(0, index));
        }
        short decimal = parseDecimal(string, index);
        return create(value, decimal);
    }

    private static short parseDecimal(String string) {
        return parseDecimal(string, string.indexOf("."));
    }

    private static short parseDecimal(String string, int index) {
        if (index == -1) {
            return 0;
        }
        String decimalAsString = string.substring(index + 1);
        int numberDigits = decimalAsString.length();
        if (numberDigits < DECIMAL_DIGITS) {
            //We need to pad it on the right with zeros
            decimalAsString += getZeros(DECIMAL_DIGITS - numberDigits);
        } else if (numberDigits > DECIMAL_DIGITS) {
            //We need to trim it to make sure it will be in range of a short
            decimalAsString = decimalAsString.substring(0, DECIMAL_DIGITS);
        }
        return Short.parseShort(decimalAsString);
    }

    private static String getZeros(int number) {
        StringBuilder zeros = new StringBuilder();
        for (int i = 0; i < number; i++) {
            zeros.append('0');
        }
        return zeros.toString();
    }

    public static FloatingLong readFromNBT(@Nullable CompoundNBT nbtTags) {
        if (nbtTags == null || nbtTags.isEmpty()) {
            return ZERO;
        }
        return create(nbtTags.getLong(NBTConstants.VALUE), nbtTags.getShort(NBTConstants.DECIMAL));
    }

    public static FloatingLong readFromBuffer(PacketBuffer buffer) {
        return new FloatingLong(buffer.readVarLong(), buffer.readShort(), false);
    }
}