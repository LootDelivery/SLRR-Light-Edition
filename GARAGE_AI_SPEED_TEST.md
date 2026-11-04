# Garage AI speed-limit test

This is a temporary test build for the ordinary garage. It does not change the player's car or the main branch.

## Install

1. Make a backup of the existing `sl/Scripts/game/Garage.class` in your game folder.
2. Download the compiled `Garage.class` from this branch.
3. Put it into the same path in the game folder: `sl/Scripts/game/Garage.class`.
4. Start the game and enter a normal garage.

Do not copy this file into `sl.rpk`; the test is supplied as a loose script class at the path above.

## What it does

- Creates a second stock car at the test position `(4, 0, 0)`.
- Uses the game's existing `Bot` class to create a normal `BOTBRAIN` controller and driver render.
- Sends the car to one fixed point 10 metres ahead of its current position.
- Samples speed every 0.10 seconds and uses `WheelRef.setBrake()` on the test car's wheels to limit it to approximately 6 km/h.
- Shows the current measured speed in the existing garage debug text.
- Stops and destroys the temporary bot and car when leaving the garage.

The test is disabled in the Race of Champions garage.

## Restore

To remove the test, restore the backup of your original `Garage.class`.

## Verification status

The modified `Garage.java` was compiled into TUFA `Garage.class` by the `SLRR-JVM-compiler` build workflow. The compile job passed. The actual AI throttle-vs-brake behaviour has not yet been tested in a running game; that is the next in-game check.
