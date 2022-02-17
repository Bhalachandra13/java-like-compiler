//The class Vehicle is not defined. This needs to throw error.

int main() {

        MountainBike m;
        m = new MountainBike();
        printInt(m.setHeight(15));

        Bicycle mb;
        mb= new Bicycle();
        printInt(mb.speedUp(10));
        return 0;

}


// base class
class Bicycle extends Vehicle{
    // the Bicycle class has two fields
    int gear;
    int speed;

    // the Bicycle class has three methods
    int applyBrake(int decrement) {
        speed = speed -  decrement;
        return speed;
    }

    int speedUp(int increment) {
        speed = speed + increment;
        return speed;
    }

}

class MountainBike {

    int seatHeight;

    int setHeight(int newValue) {
        seatHeight = newValue;
        return seatHeight;
    }

}
