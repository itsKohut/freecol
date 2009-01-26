/**
 *  Copyright (C) 2002-2007  The FreeCol Team
 *
 *  This file is part of FreeCol.
 *
 *  FreeCol is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  FreeCol is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with FreeCol.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.sf.freecol.client.gui.animation;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.logging.Logger;

import javax.swing.JLabel;
import javax.swing.JLayeredPane;

import net.sf.freecol.client.ClientOptions;
import net.sf.freecol.client.gui.Canvas;
import net.sf.freecol.client.gui.GUI;
import net.sf.freecol.common.model.Location;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.Map.Direction;

/**
 * Class for the animation of units movement.
 */
public final class UnitMoveAnimation extends Animation {
    
    private static final Logger logger = Logger.getLogger(UnitMoveAnimation.class.getName());
    
    private final Unit unit;
    private final Location currentLocation;
    private final Tile destinationTile;
    private final Point destinationPoint;
    private Point currentPoint;
    
    private JLabel unitLabel;
    private static final Integer UNIT_LABEL_LAYER = JLayeredPane.DEFAULT_LAYER;
    
    // Movement variables & constants
    private final int signalX; // If X is increasing or decreasing
    private final int signalY; // If Y is increasing or decreasing
    private final int movementRatio;
    private static final int X_RATIO = 2;
    private static final int Y_RATIO = 1;
    
    private int distanceToTarget;
    
    
    /**
     * Constructor
     * @param canvas The canvas where the animation will be drawn
     * @param unit The unit to be animated. 
     * @param direction The Direction in which the Unit will be moving.
     */
    public UnitMoveAnimation(Canvas canvas, Unit unit, Direction direction) {
        this(canvas, unit, unit.getGame().getMap().getNeighbourOrNull(direction, unit.getTile()));
    }
    
    /**
     * Constructor
     * @param canvas The canvas where the animation will be drawn
     * @param unit The unit to be animated. 
     * @param destinationTile The Tile where the Unit will be moving to.
     */
    public UnitMoveAnimation(Canvas canvas, Unit unit, Tile destinationTile) {
        super(canvas);
        this.unit = unit;
        this.destinationTile = destinationTile;
        this.currentLocation = unit.getLocation();
        
        final String key = (canvas.getClient().getMyPlayer() == unit.getOwner()) ?
                ClientOptions.MOVE_ANIMATION_SPEED
                : ClientOptions.ENEMY_MOVE_ANIMATION_SPEED;
        final int movementSpeed = canvas.getClient().getClientOptions().getInteger(key);
        
        GUI gui = canvas.getGUI();
        
        Point currP = gui.getTilePosition(unit.getTile());
        Point destP = gui.getTilePosition(destinationTile);
        if (currP != null && destP != null && movementSpeed > 0) {
            
            this.movementRatio = (int) (Math.pow(2, movementSpeed)*canvas.getGUI().getImageLibrary().getScalingFactor());
            
            unitLabel = gui.getUnitLabel(unit);
            currentPoint = gui.getUnitLabelPositionInTile(unitLabel, currP);
            destinationPoint = gui.getUnitLabelPositionInTile(unitLabel, destP);
            unitLabel.setLocation(currentPoint);
            
            canvas.add(unitLabel, UNIT_LABEL_LAYER, false);
            
            if (currentPoint.getX() == destinationPoint.getX())
                signalX = 0;
            else
                signalX = currentPoint.getX() > destinationPoint.getX() ? -1 : 1;
            
            if (currentPoint.getY() == destinationPoint.getY())
                signalY = 0;
            else
                signalY = currentPoint.getY() > destinationPoint.getY() ? -1 : 1;
            
            distanceToTarget = distance(destinationPoint, currentPoint);
            
        } else {
            // Unit is offscreen or animation is off - no need to animate
            logger.finest("Unit is offscreen or animation is off - no need to animate.");
            currentPoint = destinationPoint = null;
            signalX = signalY = 0;
            distanceToTarget = 0;
            movementRatio = 0;
        }
    }

    /**
     * Moves the Unit towards its destination point one step.
     */
    protected void readyNextFrame() {
        
        logger.finest("Calculating and setting the new unit location.");
                
        // Calculating the new coordinates for the unit            
        currentPoint.x += signalX*X_RATIO*movementRatio;        
        currentPoint.y += signalY*Y_RATIO*movementRatio;
        
        //Setting new location
        unitLabel.setLocation(currentPoint);
    }
    
    @Override
    public void animate() {
        final UnitMoveAnimation self = this;
        canvas.getGUI().executeWithUnitOutForAnimation(unit, new Runnable() {
            public void run() {
                // Painting the whole screen once to get rid of disposed dialog-boxes.
                canvas.paintImmediately(canvas.getBounds());
                try {
                    self.animateFromSuper();
                } finally {
                    canvas.remove(unitLabel, false);
                }                
            }
        });
    }
    
    /**
     * Temporary hack to call super's animate.
     */
    protected void animateFromSuper() {
        super.animate();
    }
    
    protected int distance(Point p1, Point p2) {
        return Math.abs(p1.x-p2.x) + Math.abs(p1.y-p2.y);
    }

    protected boolean isFinished() {
        if (currentPoint != null && destinationPoint != null) {
            int newDistanceToTarget = distance(currentPoint, destinationPoint);
            if (newDistanceToTarget > distanceToTarget) {
                // when moving 8 or 16 pixels at a time, we may not reach the exact destination point.
                // checking we have not overshot the destination (the distance to target is now increasing)
                distanceToTarget = 0;
                return true;
            } else if (newDistanceToTarget == 0 ) {
                // reached the exact target
                distanceToTarget = newDistanceToTarget;
                return true;
            } else {
                // the distance is still decreasing
                distanceToTarget = newDistanceToTarget;
                return false;
            }
        } else {
            distanceToTarget = 0;
            return true;
        }
    }

    protected Rectangle getAnimationArea() {
        Rectangle r1 = canvas.getGUI().getTileBounds(currentLocation.getTile());
        Rectangle r2 = canvas.getGUI().getTileBounds(destinationTile);
        return r1.union(r2);
    }
    
    protected Rectangle getDirtyAnimationArea() {
        return getAnimationArea();
    }

}
