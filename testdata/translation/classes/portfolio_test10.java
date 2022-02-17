//This test program checks for the null initialization of object.

int main(){


    return 0;
}

class Vehicle {
    int speed;
    int setSpeed(int speedN) {
        speed = speedN;
        return speed;
    }
    int applyBrake(int decrement) {
        speed = speed -  decrement;
        return speed;
    }
}

// base class
class Bicycle {
    // the Bicycle class has two fields
    int gear;
    int speed;


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
