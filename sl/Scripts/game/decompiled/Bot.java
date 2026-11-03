package java.game;

import java.io.*;
import java.util.resource.*;

public class Bot extends Racer {
    static String[] botNames;
    int fileid;
    int seed;
    float aiLevel;
    int debugid;
    VehicleDescriptor botVd;
    VehicleDescriptor nightVd;
    GameRef dummycar;
    float color;
    float optical;
    float engine;
    int imaPoliceDriver;
    int nightWins = 0;
    int nightLoses = 0;
    float bestNightQM = 0.0;
    GameRef world;
    GameRef brain;
    GameRef controller;
    int traffic_id;
    int horn;

    public Bot(int arg0, int arg1, float arg2, float arg3, float arg4, float arg5) {
        super();
        this.fileid = arg0;
        this.seed = arg1;
        this.name = constructName(this.seed);
        this.character = new RenderRef((Racer.RID_FEJ + this.fileid));
        this.color = arg2;
        this.optical = arg3;
        this.engine = arg4;
        this.aiLevel = arg5;
        if ((name.length() % 2)) {
            this.driverID = new RenderRef(GameLogic.HUMAN_OPPONENT);
        } else {
            this.driverID = new RenderRef(GameLogic.HUMAN_OPPONENT2);
        }
        setEventMask(GameRef.EVENT_COMMAND);
    }

    public Bot(int arg0, int arg1, float arg2) {
        super();
        if ((!(Bot.botNames))) {
            patchCreateBotNames();
        }
        this.fileid = arg0;
        this.seed = arg1;
        this.name = Bot.botNames[this.fileid];
        this.character = new RenderRef((Racer.RID_FEJ + this.fileid));
        int local4;
        this.color = (arg1 * 1.3678498268127441);
        local4 = this.color;
        this.color -= local4;
        this.optical = (arg1 * 3.137709856033325);
        local4 = this.optical;
        this.optical -= local4;
        this.engine = (arg1 * 4.758349895477295);
        local4 = this.engine;
        this.engine -= local4;
        this.engine = (1.0 + (this.engine * arg2));
        this.optical += 1.0;
        this.aiLevel = arg2;
        if ((name.length() % 2)) {
            this.driverID = new RenderRef(GameLogic.HUMAN_OPPONENT);
        } else {
            this.driverID = new RenderRef(GameLogic.HUMAN_OPPONENT2);
        }
        setEventMask(GameRef.EVENT_COMMAND);
    }

    public RenderRef getMarker() {
        if ((this.club == 2)) {
            return Marker.RR_CAR3;
        }
        if ((this.club == 1)) {
            return Marker.RR_CAR2;
        }
        return Marker.RR_CAR1;
    }

    public float getCarPrestige() {
        if (((!(this.car)) && this.botVd)) {
            return botVd.estimatePrestige();
        }
        return super.getCarPrestige();
    }

    public void setDriverObject(int arg0) {
        this.driverID = new RenderRef(arg0);
    }

    public void createCar(GameRef arg0, String arg1) {
        Vehicle local3 = Vehicle.load(arg1, this);
        if ((!(local3))) {
            System.exit(("Fatal: Cannot create car using file " + arg1));
        }
        createCar(arg0, local3);
    }

    public void createCar(GameRef arg0) {
        Vehicle local2;
        if (this.botVd) {
            local2 = new Vehicle(this, botVd.id, botVd.colorIndex, botVd.optical, botVd.power, botVd.wear, botVd.tear);
        } else {
            System.exit("Bot.createCar(): VehicleDescriptor null");
        }
        createCar(arg0, local2);
    }

    public void createCar(GameRef arg0, Vehicle arg1) {
        this.world = arg0;
        deleteCar();
        this.car = arg1;
        this.debugid = car.id();
        enterCar(this.car);
    }

    public void deleteCar() {
        if (this.car) {
            leaveCar(0);
            if (car.id()) {
                car.destroy();
            }
            this.car = null;
        }
    }

    public void enterCar(Vehicle arg0) {
        if ((!(arg0))) {
            return;
        }
        arg0.setTransmission(Vehicle.TRANSMISSION_SEMIAUTO);
        this.brain = new GameRef(this.world, sl:0x6er, "", "BOTBRAIN");
        this.controller = this.brain;
        brain.queueEvent(null, GameType.EVENT_COMMAND, ("AI_level " + this.aiLevel));
        arg0.command("corpse 0 0");
        this.render = new RenderRef(this.world, this.driverID, "botfigura");
        brain.queueEvent(null, GameType.EVENT_COMMAND, ("renderinstance " + render.id()));
        brain.queueEvent(null, GameType.EVENT_COMMAND, ("controllable " + arg0.id()));
        brain.queueEvent(null, GameType.EVENT_COMMAND, "AI_suspend");
    }

    public void leaveCar(int arg0) {
        if ((!(arg0))) {
            leaveTraffic();
        }
        if (this.render) {
            render.destroy();
        }
        if (this.brain) {
            brain.queueEvent(null, GameType.EVENT_COMMAND, ("leave " + car.id()));
            brain.destroy();
            this.brain = null;
            this.controller = null;
        }
        if (arg0) {
            if (this.car) {
                car.release();
                this.car = null;
            }
        }
    }

    public void handleEvent(GameRef arg0, int arg1, int arg2) {
        if ((arg1 == GameRef.EVENT_TIME)) {
            clearEventMask(GameRef.EVENT_TIME);
            reJoinTraffic();
        }
    }

    public void handleEvent(GameRef arg0, int arg1, String arg2) {
        if ((arg1 == GameRef.EVENT_COMMAND)) {
            String local4 = arg2.token(0);
            if ((local4 == "ai_info_entertraffic")) {
                this.traffic_id = arg2.token(1).intValue();
                if (this.imaPoliceDriver) {
                    remNotification(this.car, GameRef.EVENT_COMMAND);
                    leaveCar(1);
                } else {
                    brain.destroy();
                    this.brain = null;
                    this.controller = null;
                    RenderRef local5 = new RenderRef(this.world, this.driverID, "botfigura-leavetraffic");
                    car.command(("corpse 0 " + local5.id()));
                    car.release();
                    this.car = null;
                }
            } else {
                if ((local4 == "ai_info_leavetraffic")) {
                    this.traffic_id = 0;
                    this.car = new Vehicle(this.dummycar);
                    this.brain = new GameRef(this.world, sl:0x6er, "", "BOTBRAIN");
                    this.controller = this.brain;
                    dummycar.command("corpse 0 0");
                    RenderRef local5_2 = new RenderRef(this.world, this.driverID, "botfigura-leavetraffic");
                    brain.command(("renderinstance " + local5_2.id()));
                    brain.queueEvent(null, GameType.EVENT_COMMAND, ("controllable " + car.id()));
                    stop();
                    setEventMask(GameRef.EVENT_TIME);
                    addTimer(3.0, 0);
                }
            }
        }
    }

    public void setTrafficBehaviour(int arg0) {
        if ((this.traffic_id && (this.world instanceof GroundRef))) {
            ((GroundRef) this.world).setTrafficCarBehaviour(this.traffic_id, arg0);
        }
    }

    public void joinTraffic(Vector3 arg0) {
        if ((!(this.traffic_id))) {
            beStupid();
            this.traffic_id = world.addTrafficCar(this.car, arg0);
        }
    }

    public void reJoinTraffic() {
        if ((!(this.traffic_id))) {
            if (this.brain) {
                brain.queueEvent(null, GameType.EVENT_COMMAND, "AI_GoToTraffic");
            }
        }
    }

    public void leaveTraffic() {
        if (this.traffic_id) {
            world.remTrafficCar(this.traffic_id);
            this.traffic_id = 0;
        }
    }

    public void beStupid() {
        brain.queueEvent(null, GameType.EVENT_COMMAND, "AI_suspend");
    }

    public void followCar(GameRef arg0, float arg1) {
        leaveTraffic();
        brain.queueEvent(null, GameType.EVENT_COMMAND, ((("AI_follow 0,0," + arg1) + " ") + arg0.id()));
    }

    public void stopCar(GameRef arg0) {
        leaveTraffic();
        brain.queueEvent(null, GameType.EVENT_COMMAND, ("AI_follow 0,0,-2 " + arg0.id()));
    }

    public void startRace(Vector3 arg0, Racer arg1) {
        leaveTraffic();
        brain.queueEvent(null, GameType.EVENT_COMMAND, ((("AI_race " + arg0.toString()) + " ") + arg1.car.id()));
    }

    public void driveStraightTo(Vector3 arg0) {
        leaveTraffic();
        brain.queueEvent(null, GameType.EVENT_COMMAND, ("AI_GoToTarget " + arg0.toString()));
    }

    public void pressHorn() {
        if (this.horn) {
            return;
        }
        if (this.car) {
            car.queueEvent(null, GameType.EVENT_COMMAND, "sethorn 1");
            this.horn = 1;
        } else {
            if (this.dummycar) {
                dummycar.queueEvent(null, GameType.EVENT_COMMAND, "sethorn 1");
                this.horn = 1;
            }
        }
    }

    public void releaseHorn() {
        if ((!(this.horn))) {
            return;
        }
        if (this.car) {
            car.queueEvent(null, GameType.EVENT_COMMAND, "sethorn 0");
            this.horn = 0;
        } else {
            if (this.dummycar) {
                dummycar.queueEvent(null, GameType.EVENT_COMMAND, "sethorn 0");
                this.horn = 0;
            }
        }
    }

    public void stop() {
        leaveTraffic();
        if (this.brain) {
            brain.queueEvent(null, GameType.EVENT_COMMAND, "AI_stop");
        }
    }

    public void followSplineTrack(float arg0, String arg1, int arg2) {
        brain.queueEvent(null, GameRef.EVENT_COMMAND, ((((("AI_spline " + arg0) + " ") + arg1) + " ") + arg2));
    }

    public String constructName(int arg0) {
        String[] local2 = new String[20];
        local2[0] = "Dr. ";
        local2[1] = "Old ";
        String[] local3 = new String[12];
        local3[0] = "John ";
        local3[1] = "David ";
        local3[2] = "Bill ";
        local3[3] = "Stewart ";
        local3[4] = "Joe ";
        local3[5] = "Sam ";
        local3[6] = "Alan ";
        local3[7] = "Marc ";
        local3[8] = "Jason ";
        local3[9] = "Sean ";
        local3[10] = "Tony ";
        local3[11] = "Leo ";
        String[] local4 = new String[40];
        local4[0] = "'Lucky' ";
        local4[1] = "'Speedy' ";
        local4[2] = "'Swifty' ";
        local4[3] = "'Ugly' ";
        local4[4] = "'Bighead' ";
        local4[5] = "'Bugsy' ";
        local4[6] = "'Fuzzy' ";
        local4[7] = "'Wacky' ";
        local4[8] = "'Scottish' ";
        local4[9] = "'Danish' ";
        local4[10] = "'Looser' ";
        local4[11] = "'GearHead' ";
        local4[12] = "'Skinny' ";
        String[] local5 = new String[22];
        local5[0] = "Galahad";
        local5[1] = "Butterfly";
        local5[2] = "Robertson";
        local5[3] = "Cocker";
        local5[4] = "Johnson";
        local5[5] = "Livingstone";
        local5[6] = "Dunnigan";
        local5[7] = "Little";
        local5[8] = "Luciano";
        local5[9] = "Evans";
        local5[10] = "Murphy";
        local5[11] = "Speaker";
        local5[12] = "Sterkovic";
        local5[13] = "Scott";
        local5[14] = "McDonell";
        local5[15] = "Bonnett";
        local5[16] = "Bakers";
        local5[17] = "Perkins";
        local5[18] = "Olson";
        local5[19] = "Polansky";
        local5[20] = "O'Connor";
        local5[21] = "Kozak";
        String[] local6 = new String[20];
        local6[0] = " jr.";
        return ((local3[((arg0 * 19) % local3.length)] + local5[((arg0 * 23) % local5.length)]) + local6[((arg0 * 17) % local6.length)]);
    }

    public String getPrestigeString() {
        return getPrestigeString((-(1)));
    }

    public String getPrestigeString(int arg0) {
        int local2;
        int local3;
        int local4;
        local2 = (this.prestige * Racer.PRESTIGE_SCALE);
        if (this.car) {
            local3 = (car.getPrestige() * Racer.VHC_PRESTIGE_SCALE);
        } else {
            if (((arg0 == 0) || (arg0 == 1))) {
                local3 = (nightVd.estimatePrestige() * Racer.VHC_PRESTIGE_SCALE);
            } else {
                local3 = (botVd.estimatePrestige() * Racer.VHC_PRESTIGE_SCALE);
            }
        }
        local4 = ((local2 * 0.5) + (local3 * 0.5));
        return ((local2 + "/") + local3);
    }

    public void patchCreateBotNames() {
        Bot.botNames = new String[(GameLogic.CLUBS * GameLogic.CLUBMEMBERS)];
        Bot.botNames[0] = "Trevor Banks";
        Bot.botNames[1] = "Sam Cranston";
        Bot.botNames[2] = "Nick Blitzer";
        Bot.botNames[3] = "Kip Dragonarm";
        Bot.botNames[4] = "Marcus Lewis";
        Bot.botNames[5] = "Wolfe Johnson";
        Bot.botNames[6] = "Oliver Stranhand";
        Bot.botNames[7] = "Otaranto Nogadachi";
        Bot.botNames[8] = "Pete McMorrow";
        Bot.botNames[9] = "Kenny Surestar";
        Bot.botNames[10] = "Victor Speedwheel";
        Bot.botNames[11] = "Darren O'Donnel";
        Bot.botNames[12] = "Bob Rockslate";
        Bot.botNames[13] = "Race Slade";
        Bot.botNames[14] = "Freddie Jones";
        Bot.botNames[15] = "Jennifer Angelstone";
        Bot.botNames[16] = "Cody Emerson";
        Bot.botNames[17] = "Bradley Wheelwright";
        Bot.botNames[18] = "Blake Kane";
        Bot.botNames[19] = "Buck Ironhead";
        Bot.botNames[20] = "Kane Magnum";
        Bot.botNames[21] = "Sebastian Fox";
        Bot.botNames[22] = "Old Sean Dunnigan";
        Bot.botNames[23] = "Max Sharpaxe";
        Bot.botNames[24] = "Shayne Phoenix";
        Bot.botNames[25] = "Crash Bradley";
        Bot.botNames[26] = "Dash Thunder";
        Bot.botNames[27] = "Dick Midnightwill";
        Bot.botNames[28] = "Monica Slangster";
        Bot.botNames[29] = "Lance Kane";
        Bot.botNames[30] = "Darren Lonemidnight";
        Bot.botNames[31] = "Joey Barrow";
        Bot.botNames[32] = "Toranuchi Sizukan";
        Bot.botNames[33] = "Buzz Shadowhand";
        Bot.botNames[34] = "Tex Rockstrike";
        Bot.botNames[35] = "Buck Armstrong";
        Bot.botNames[36] = "Pierce Camspear";
        Bot.botNames[37] = "Derek Buchanan";
        Bot.botNames[38] = "Winford Pierce";
        Bot.botNames[39] = "Debra Wingside";
        Bot.botNames[40] = "Jacky Slater";
        Bot.botNames[41] = "Brian Corrigan";
        Bot.botNames[42] = "Unorua Starchest";
        Bot.botNames[43] = "Jimmy Ranger";
        Bot.botNames[44] = "Yagimoto Iimu";
        Bot.botNames[45] = "Benton Fury";
        Bot.botNames[46] = "Dean Sinatra";
        Bot.botNames[47] = "Bradley Savage";
        Bot.botNames[48] = "Remington Benson";
        Bot.botNames[49] = "Rocky Braveshot";
        Bot.botNames[50] = "Ace Cranston";
        Bot.botNames[51] = "Johnnie Marshall";
        Bot.botNames[52] = "Mitchell Campbell";
        Bot.botNames[53] = "Nathaniel Owens";
        Bot.botNames[54] = "Raymond Payne";
        Bot.botNames[55] = "Mitsuoma Sabodo";
        Bot.botNames[56] = "William Dawson";
        Bot.botNames[57] = "Charlie Wilson";
        Bot.botNames[58] = "Bad Anderson";
        Bot.botNames[59] = "Clayton Hudson";
    }

    public void save(File arg0) {
        super.save(arg0);
        int local2 = 1;
        arg0.write(local2);
        if ((local2 >= 1)) {
            arg0.write(this.nightWins);
            arg0.write(this.nightLoses);
            arg0.write(this.bestNightQM);
        }
    }

    public void load(File arg0) {
        super.load(arg0);
        int local2;
        local2 = arg0.readInt();
        if ((local2 >= 1)) {
            this.nightWins = arg0.readInt();
            this.nightLoses = arg0.readInt();
            this.bestNightQM = arg0.readFloat();
        }
    }

}
