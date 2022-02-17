//The program should throw error at line 11 as conversion from parentclass to childs class is not allowed.

int main() {

        Bicycle mb;
        mb= new MountainBike();
        printInt(mb.setHeight(10));

        MountainBike m;
        m = new Bicycle();
        printInt(m.setHeight(15));
        return 0;
}


// base class
class Bicycle {
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

// derived class
class MountainBike extends Bicycle {

    // the MountainBike subclass adds one more field
    int seatHeight;

    // the MountainBike subclass adds one more method
    int setHeight(int newValue) {
        seatHeight = newValue;
        return seatHeight;
    }

}
