# bmw-dash

This is simple application that utilizes CANbus i/f attached to BMW car infotament bus.
For better understanding, some related reverse enegineering stuff can be found here: http://www.loopybunny.co.uk/CarPC/k_can.html

In this example we have Usb2Can, Bluetooth2Can target drivers as well as Wifi2Can endpoint (not fully tested).
Adding a new platform specific driver is straight forward (see CanDriver.java & FakeDevice.java), 
so it is easy to use even CAN controller on embedded device (SoC), where low level layer is put into JNI .so lib.

Currently the dash board exposes info about current speed, RPM (using fancy gauge views), and engine temperature.

BMW dash board wiki -> https://github.com/sygi1982/bmw-dash/wiki
