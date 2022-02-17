//This test program checks for conversion of child class object to parent class
// This should invoke setheight function without any error.

int main(){

    int x;
    Bicycle mb;
    mb = new MountainBike();
    x = mb.setHeight(10);

    return 0;
}


// base class
class Bicycle {
    // the Bicycle class has two fields
    int gear;
    int speed;

    int setSpeed(int speedN) {
        speed = speedN;
        return speed;
    }

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
