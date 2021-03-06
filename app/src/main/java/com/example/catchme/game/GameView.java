package com.example.catchme.game;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.example.catchme.GameActivity;
import com.example.catchme.R;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;


public class GameView extends View {

    enum Status {
        RUN,
        PAUSE,
        OVER,
    }

    static private final int GROUND_LEVEL_NUM = 5;
    static private final int MIN_SEGMENT_NUM = 2;
    static private final int MAX_SEGMENT_NUM = 5;
    static private final float BG_VELOCITY_RATE = 0.002f;
    static private final float OBJECT_VELOCITY_RATE = 0.005f;
    static private final float GAP_RATE = 0.05f;
    static private final float PAUSE_RATE = 0.03f;
    static private final int SCORE_RATE = 20;
    static private final int SOUND_NUM = 5;

    private Status status = Status.RUN;
    private Character character;
    private Background background;
    private List<Floor> floors;
    private List<GameObject> enemies;
    private List<GameObject> potions;
    private List<Explosion> explosions;
    private List<Bullet> bullets;

    Map<GameActivity.Sprites, Bitmap> bitmaps;
    private float score = 0;
    private long frame = 0;
    private boolean clicked = false;
    private Paint paint;
    private Paint textPaint;
    private float fontSize = 0;
    private float borderSize = 0;
    private float density = 0;
    private float pauseOffset = -1;
    private Rect continueRect = null;
    private SoundPool soundPool = null;
    private int bgmId = 0;
    private int crashId = 0;
    private int jumpId = 0;
    private int explosionId = 0;
    private int getId = 0;
    private int streamId = 0;
    private int loadedNum = 0;
    private boolean soundLoaded = false;

    public GameView(Context context) {
        super(context);
        init();
    }

    public GameView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GameView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        borderSize = 2;
        floors = new ArrayList<>();
        enemies = new ArrayList<>();
        potions = new ArrayList<>();
        explosions = new ArrayList<>();
        bullets = new ArrayList<>();
        paint = new Paint();
        paint.setStyle(Paint.Style.FILL);
        textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.FAKE_BOLD_TEXT_FLAG);
        textPaint.setColor(0xff000000);
        fontSize = textPaint.getTextSize();
        fontSize *= density;
        textPaint.setTextSize(fontSize);
        borderSize *= density;
        SoundPool.Builder builder = new SoundPool.Builder();
        builder.setMaxStreams(8);
        AudioAttributes.Builder attrBuilder = new AudioAttributes.Builder();
        attrBuilder.setLegacyStreamType(AudioManager.STREAM_MUSIC);
        builder.setAudioAttributes(attrBuilder.build());
        soundPool = builder.build();
        soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                synchronized (SoundPool.class) {
                    loadedNum += 1;
                    if (!soundLoaded && loadedNum >= SOUND_NUM) {
                        soundLoaded = true;
                    }
                }
            }
        });
        bgmId = soundPool.load(this.getContext(), R.raw.background,1);
        crashId = soundPool.load(this.getContext(), R.raw.crash,1);
        jumpId = soundPool.load(this.getContext(), R.raw.jump,1);
        explosionId = soundPool.load(this.getContext(), R.raw.explosion,1);
        getId = soundPool.load(this.getContext(), R.raw.get,1);
    }

    private void playBgm() {
        if (soundLoaded) {
            streamId = soundPool.play(bgmId, 1, 1, 0, -1, 1);
        }
    }

    private void playJump() {
        if (soundLoaded) {
            soundPool.play(jumpId, 1, 1, 0, 0, 1);
        }
    }

    private void playCrash() {
        if (soundLoaded) {
            soundPool.pause(streamId);
            soundPool.play(crashId, 1, 1, 0, 0, 1);
        }
    }

    private void playExplode() {
        if (soundLoaded) {
            soundPool.play(explosionId, 1, 1, 0, 0, 1);
        }
    }

    private void playGet() {
        if (soundLoaded) {
            soundPool.play(getId, 1, 1, 0, 0, 1);
        }
    }

    List<Bullet> getBullets() {
        return bullets;
    }

    Character getCharacter() {
        return character;
    }

    void addScore(int score, boolean get) {
        this.score += score;
        if (get) {
            playGet();
        }
    }

    public void start(Map<GameActivity.Sprites, Bitmap> bitmaps) {
        this.bitmaps = bitmaps;
        postInvalidate();
    }

    private void restart() {
        frame = 0;
        status= Status.RUN;
    }

    private void initGame(Canvas canvas) {
        score = 0;
        Background.setVelocityRate(BG_VELOCITY_RATE);
        LeftMovingSprite.setVelocityRate(OBJECT_VELOCITY_RATE);
        clicked = false;
        Floor firstFloor = new Floor(bitmaps.get(GameActivity.Sprites.FLOOR), MAX_SEGMENT_NUM);
        firstFloor.centerTo(canvas.getWidth() / 2, floorLevelY(canvas, 0));
        firstFloor.setLevel(0);
        floors.clear();
        enemies.clear();
        potions.clear();
        explosions.clear();
        bullets.clear();
        floors.add(firstFloor);
        character = new Character(bitmaps.get(GameActivity.Sprites.CHARACTER));
        character.adjust(canvas, this);
        background = new Background(bitmaps.get(GameActivity.Sprites.BACKGROUND));
        status = Status.RUN;
    }

    private void generateFloorIfCan(Canvas canvas) {
        Floor rightFloor = floors.get(floors.size() - 1);
        if (rightFloor.getX() < canvas.getWidth()) {
            genFloorAndObject(canvas, rightFloor);
        }
    }

    private void genFloorAndObject(Canvas canvas, Floor rightFloor) {
        Random r = new Random();
        int segments = r.nextInt(MAX_SEGMENT_NUM - MIN_SEGMENT_NUM + 1)
                + MIN_SEGMENT_NUM;
        Floor floor = new Floor(bitmaps.get(GameActivity.Sprites.FLOOR), segments);
        int level;
        int rightLevel = rightFloor.getLevel();
        if (rightLevel == 0 || rightLevel < GROUND_LEVEL_NUM - 1 && r.nextInt(2) > 0) {
            level = rightLevel + 1;
        } else {
            level = r.nextInt(rightLevel);
        }
        floor.centerTo(0, floorLevelY(canvas, level));
        floor.setLevel(level);
        floor.setX(rightFloor.getX() + rightFloor.getWidth() + canvas.getWidth() * GAP_RATE);
        floors.add(floor);
        if (segments <= MIN_SEGMENT_NUM + 1) {
            return;
        }
        int move = r.nextInt(3);
        boolean horizontal = move == 1;
        boolean vertical = move == 2;
        if (r.nextInt(2) > 0) {
            GameObject enemy = new Monster(bitmaps.get(GameActivity.Sprites.MONSTER), horizontal, vertical);
            enemy.bondToFloor(floor);
            enemies.add(enemy);
        } else {
            GameObject potion;
            if (r.nextInt(2) > 0) {
                potion = new Gun(bitmaps.get(GameActivity.Sprites.GUN), horizontal, vertical);
            } else {
                potion = new Coin(bitmaps.get(GameActivity.Sprites.COIN), horizontal, vertical);
            }
            potion.bondToFloor(floor);
            potions.add(potion);
        }
    }

    private float floorLevelY(Canvas canvas, int level) {
        return canvas.getHeight() / (GROUND_LEVEL_NUM + 2) * (GROUND_LEVEL_NUM  - level);
    }

    float getCenterFloorY(float x) {
        float y = 0;
        for (Floor floor: floors) {
            if (floor.getX() <= x && floor.getX() + floor.getWidth() >= x) {
                y = floor.getY();
            }
        }
        return y;
    }

    void shoot() {
        Bullet bullet = new Bullet(bitmaps.get(GameActivity.Sprites.BULLET));
        bullet.bondToCharacter(character);
        bullets.add(bullet);
    }

    void explode(Sprite sprite) {
        Explosion explosion = new Explosion(bitmaps.get(GameActivity.Sprites.EXPLOSION));
        explosion.moveTo(sprite.getX(), sprite.getY());
        explosions.add(explosion);
        playExplode();
    }

    void gameOver() {
        if (status != Status.OVER) {
            status = Status.OVER;
            playCrash();
        }
    }

    private void deleteDestroyed() {
        Iterator<Floor> floorIterator = floors.iterator();
        while (floorIterator.hasNext()) {
            if (floorIterator.next().isDestroyed()) {
                floorIterator.remove();
            }
        }
        Iterator<GameObject> enemyIterator = enemies.iterator();
        while (enemyIterator.hasNext()) {
            if (enemyIterator.next().isDestroyed()) {
                enemyIterator.remove();
            }
        }
        Iterator<GameObject> potionIterator = potions.iterator();
        while (potionIterator.hasNext()) {
            if (potionIterator.next().isDestroyed()) {
                potionIterator.remove();
            }
        }
        Iterator<Bullet> bulletIterator = bullets.iterator();
        while (bulletIterator.hasNext()) {
            if (bulletIterator.next().isDestroyed()) {
                bulletIterator.remove();
            }
        }
        Iterator<Explosion> explosionIterator = explosions.iterator();
        while (explosionIterator.hasNext()) {
            if (explosionIterator.next().isDestroyed()) {
                explosionIterator.remove();
            }
        }
    }

    private void drawDynamic(Canvas canvas) {
        background.draw(canvas, paint, this);
        drawScore(canvas);
        for (GameObject potion: potions) {
            potion.draw(canvas, paint, this);
        }
        for (GameObject enemy: enemies) {
            enemy.draw(canvas, paint, this);
        }
        for (Bullet bullet: bullets) {
            bullet.draw(canvas, paint, this);
        }
        for (Explosion explosion: explosions) {
            explosion.draw(canvas, paint, this);
        }
        character.draw(canvas, paint, this);
        for (Floor floor: floors) {
            floor.draw(canvas, paint, this);
        }
    }

    private void drawStatic(Canvas canvas) {
        background.onDraw(canvas, paint, this);
        for (GameObject potion: potions) {
            potion.onDraw(canvas, paint, this);
        }
        for (GameObject enemy: enemies) {
            enemy.onDraw(canvas, paint, this);
        }
        for (Bullet bullet: bullets) {
            bullet.onDraw(canvas, paint, this);
        }
        for (Explosion explosion: explosions) {
            explosion.onDraw(canvas, paint, this);
        }
        character.onDraw(canvas, paint, this);
        for (Floor floor: floors) {
            floor.onDraw(canvas, paint, this);
        }
    }

    private float calVelocityRate() {
        return ((int)score / 100) * 0.1f + 1 ;
    }

    private void drawGameRunning(Canvas canvas) {
        if (frame == 0) {
            initGame(canvas);
        }
        if (clicked) {
            character.jump();
            playJump();
            clicked = false;
        }
        frame++;
        score += SCORE_RATE * LeftMovingSprite.getVelocityRate();
        float newRate = calVelocityRate();
        if (newRate * OBJECT_VELOCITY_RATE > LeftMovingSprite.getVelocityRate()) {
            LeftMovingSprite.setVelocityRate(newRate * OBJECT_VELOCITY_RATE);
            Background.setVelocityRate(newRate * BG_VELOCITY_RATE);
        }
        deleteDestroyed();
        generateFloorIfCan(canvas);
        drawDynamic(canvas);
        if (character.getY() + character.getHeight() >= canvas.getHeight()) {
            gameOver();
        }
        postInvalidate();
    }

    private void drawGamePause(Canvas canvas) {
        drawStatic(canvas);
        drawScoreDialog(canvas, "继续");
        postInvalidate();
    }

    private void drawGameOver(Canvas canvas) {
        drawStatic(canvas);
        drawScoreDialog(canvas, "重新开始");
        postInvalidate();
    }

    private void drawScore(Canvas canvas) {
        Bitmap pauseBitmap = bitmaps.get(GameActivity.Sprites.PAUSE);
        if (pauseOffset <0) {
            pauseOffset = PAUSE_RATE * canvas.getHeight();
        }
        canvas.drawBitmap(pauseBitmap, pauseOffset, pauseOffset, paint);
        float scoreLeft = pauseOffset + pauseBitmap.getWidth() + pauseOffset;
        float scoreTop = fontSize + pauseOffset + pauseBitmap.getHeight() / 2 - fontSize / 2;
        canvas.drawText("分数：" + (int)score, scoreLeft, scoreTop, textPaint);
    }

    private void drawScoreDialog(Canvas canvas, String operation){
        int canvasWidth = canvas.getWidth();
        int canvasHeight = canvas.getHeight();
        float originalFontSize = textPaint.getTextSize();
        Paint.Align originalFontAlign = textPaint.getTextAlign();
        int originalColor = paint.getColor();
        Paint.Style originalStyle = paint.getStyle();
        int w1 = (int)(20.0 / 360.0 * canvasWidth);
        int w2 = canvasWidth - 2 * w1;
        int buttonWidth = (int)(140.0 / 360.0 * canvasWidth);

        int h1 = (int)(150.0 / 558.0 * canvasHeight);
        int h2 = (int)(60.0 / 558.0 * canvasHeight);
        int h3 = (int)(124.0 / 558.0 * canvasHeight);
        int h4 = (int)(76.0 / 558.0 * canvasHeight);
        int buttonHeight = (int)(42.0 / 558.0 * canvasHeight);

        canvas.translate(w1, h1);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFD7DDDE);
        Rect rect1 = new Rect(0, 0, w2, canvasHeight - 2 * h1);
        canvas.drawRect(rect1, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0xFF515151);
        paint.setStrokeWidth(borderSize);
        paint.setStrokeJoin(Paint.Join.ROUND);
        canvas.drawRect(rect1, paint);
        textPaint.setTextSize(fontSize);
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("当前分数", w2 / 2, (h2 - fontSize) / 2 + fontSize, textPaint);
        canvas.translate(0, h2);
        canvas.drawLine(0, 0, w2, 0, paint);
        String allScore = String.valueOf((int)score);
        canvas.drawText(allScore, w2 / 2, (h3 - fontSize) / 2 + fontSize, textPaint);
        canvas.translate(0, h3);
        canvas.drawLine(0, 0, w2, 0, paint);
        Rect rect2 = new Rect();
        rect2.left = (w2 - buttonWidth) / 2;
        rect2.right = w2 - rect2.left;
        rect2.top = (h4 - buttonHeight) / 2;
        rect2.bottom = h4 - rect2.top;
        canvas.drawRect(rect2, paint);
        canvas.translate(0, rect2.top);
        canvas.drawText(operation, w2 / 2, (buttonHeight - fontSize) / 2 + fontSize, textPaint);
        continueRect = new Rect(rect2);
        continueRect.left = w1 + rect2.left;
        continueRect.right = continueRect.left + buttonWidth;
        continueRect.top = h1 + h2 + h3 + rect2.top;
        continueRect.bottom = continueRect.top + buttonHeight;

        textPaint.setTextSize(originalFontSize);
        textPaint.setTextAlign(originalFontAlign);
        paint.setColor(originalColor);
        paint.setStyle(originalStyle);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (soundLoaded && streamId == 0) {
            playBgm();
        } else if (!soundLoaded) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (status == Status.RUN) {
            drawGameRunning(canvas);
        } else if (status == Status.PAUSE) {
            drawGamePause(canvas);
        } else if (status == Status.OVER) {
            drawGameOver(canvas);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int touchType = event.getAction();
        if (touchType == MotionEvent.ACTION_DOWN) {
            if (status == Status.RUN) {
                if (pauseOffset >= 0 && event.getX() >= pauseOffset && event.getY() >=pauseOffset
                        && event.getX() <= pauseOffset + bitmaps.get(GameActivity.Sprites.PAUSE).getWidth()
                        && event.getY() <= pauseOffset + bitmaps.get(GameActivity.Sprites.PAUSE).getHeight()) {
                    status = Status.PAUSE;
                    if (soundLoaded) {
                        soundPool.pause(streamId);
                    }
                } else {
                    clicked = true;
                }
            } else if (status == Status.PAUSE) {
                if (continueRect.contains((int)event.getX(), (int)event.getY())) {
                    status = Status.RUN;
                    playBgm();
                }
            } else if (status == Status.OVER) {
                if (continueRect.contains((int) event.getX(), (int) event.getY())) {
                    playBgm();
                    restart();
                }
            }
        }
        return true;
    }
}
