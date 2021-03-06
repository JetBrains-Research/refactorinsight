package misc;
public class Bicycle {

    public int gear2;
    public int speed;
    public PriceWithDiscount price;
    public int seatHeight;
    static int discount;

    public Bicycle(int gear, int speed, PriceWithDiscount price, int seatHeight) {
        this.gear2 = gear;
        this.speed = speed;
        this.speed *= 2;
        this.price = price;
        this.seatHeight = seatHeight;
    }

    public void applyBrakeAndDecrement(String decrement) {
        speed -= 1;
    }

    public String toString() {
        String speed2 = "speed of bicycle is " + speed;
        final String string = gearToString();
        return string + speed2;
    }

    private String gearToString() {
        return "No of gears are " + gear2 + "\n";
    }

    private int doubleSpeed(Integer a) {
        this.speed *= 2 + a;
        return this.speed;
    }

    public int getPrice() {
        return price.getPrice();
    }

    public PriceWithDiscount get() {
        return price;
    }

}
