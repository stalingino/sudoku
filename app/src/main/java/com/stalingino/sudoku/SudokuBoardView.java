package com.stalingino.sudoku;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/** Draws the 9x9 grid and reports cell taps. Holds no game rules of its own. */
public class SudokuBoardView extends View {

    public interface OnCellSelectedListener {
        void onCellSelected(int index);
    }

    private int[] givens = new int[81];
    private int[] values = new int[81];
    private int[] notes = new int[81];
    private boolean[] conflicts = new boolean[81];
    private boolean[] wrong = new boolean[81];
    private int selected = -1;
    private boolean solved;
    private boolean highlightSame = true;
    private OnCellSelectedListener listener;

    private final Paint thinLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thickLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint();
    private final Paint givenText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint userText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint noteText = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final int colorBoard, colorPeer, colorSameDigit, colorSelected, colorConflictBg,
            colorGiven, colorUser, colorError, colorNote, colorSolved;

    private float cell;
    private float originX;
    private float originY;

    public SudokuBoardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        colorBoard = context.getColor(R.color.board_bg);
        colorPeer = context.getColor(R.color.board_peer);
        colorSameDigit = context.getColor(R.color.board_same_digit);
        colorSelected = context.getColor(R.color.board_selected);
        colorConflictBg = context.getColor(R.color.board_conflict_bg);
        colorGiven = context.getColor(R.color.text_given);
        colorUser = context.getColor(R.color.text_user);
        colorError = context.getColor(R.color.text_error);
        colorNote = context.getColor(R.color.text_note);
        colorSolved = context.getColor(R.color.board_solved);

        float density = getResources().getDisplayMetrics().density;
        thinLine.setColor(context.getColor(R.color.grid_thin));
        thinLine.setStrokeWidth(1f * density);
        thickLine.setColor(context.getColor(R.color.grid_thick));
        thickLine.setStrokeWidth(2.5f * density);
        thickLine.setStrokeCap(Paint.Cap.SQUARE);

        givenText.setTextAlign(Paint.Align.CENTER);
        givenText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        userText.setTextAlign(Paint.Align.CENTER);
        noteText.setTextAlign(Paint.Align.CENTER);
        noteText.setColor(colorNote);

        setFocusable(true);
        setHapticFeedbackEnabled(true);
    }

    public void setHighlightSameDigits(boolean on) {
        highlightSame = on;
        invalidate();
    }

    public void setOnCellSelectedListener(OnCellSelectedListener l) {
        listener = l;
    }

    public void setState(int[] givens, int[] values, int[] notes, boolean[] conflicts,
                         boolean[] wrong, int selected, boolean solved) {
        this.givens = givens;
        this.values = values;
        this.notes = notes;
        this.conflicts = conflicts;
        this.wrong = wrong;
        this.selected = selected;
        this.solved = solved;
        updateContentDescription();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);
        int hMode = MeasureSpec.getMode(heightMeasureSpec);
        if (hMode == MeasureSpec.EXACTLY) {
            // Take the whole slot (e.g. a weighted area); the grid is centred in onDraw.
            setMeasuredDimension(w, h);
        } else {
            int size = (hMode == MeasureSpec.UNSPECIFIED) ? w : Math.min(w, h);
            setMeasuredDimension(size, size);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float pad = thickLine.getStrokeWidth() / 2f;
        float size = Math.min(w, h) - pad * 2;
        cell = size / 9f;
        originX = (w - size) / 2f;
        originY = (h - size) / 2f;
        givenText.setTextSize(cell * 0.62f);
        userText.setTextSize(cell * 0.62f);
        noteText.setTextSize(cell * 0.27f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float size = cell * 9;
        fill.setColor(solved ? colorSolved : colorBoard);
        canvas.drawRect(originX, originY, originX + size, originY + size, fill);

        int selValue = selected >= 0 ? values[selected] : 0;
        int sr = selected / 9, sc = selected % 9;

        for (int i = 0; i < 81; i++) {
            int r = i / 9, c = i % 9;
            int bg = 0;
            if (selected >= 0) {
                boolean peer = r == sr || c == sc || (r / 3 == sr / 3 && c / 3 == sc / 3);
                if (i == selected) bg = colorSelected;
                else if (highlightSame && selValue != 0 && values[i] == selValue) bg = colorSameDigit;
                else if (peer) bg = colorPeer;
            }
            if ((conflicts[i] || wrong[i]) && i != selected) bg = colorConflictBg;
            if (bg != 0) {
                fill.setColor(bg);
                float x = originX + c * cell, y = originY + r * cell;
                canvas.drawRect(x, y, x + cell, y + cell, fill);
            }
        }

        for (int i = 0; i < 81; i++) {
            int r = i / 9, c = i % 9;
            float cx = originX + c * cell + cell / 2f;
            float cy = originY + r * cell + cell / 2f;
            int v = values[i];
            if (v != 0) {
                Paint p;
                if (givens[i] != 0) {
                    p = givenText;
                    p.setColor(conflicts[i] ? colorError : colorGiven);
                } else {
                    p = userText;
                    p.setColor(conflicts[i] || wrong[i] ? colorError : colorUser);
                }
                float baseline = cy - (p.descent() + p.ascent()) / 2f;
                canvas.drawText(String.valueOf(v), cx, baseline, p);
            } else if (notes[i] != 0) {
                float sub = cell / 3f;
                for (int d = 1; d <= 9; d++) {
                    if ((notes[i] & (1 << d)) == 0) continue;
                    float nx = originX + c * cell + ((d - 1) % 3) * sub + sub / 2f;
                    float ny = originY + r * cell + ((d - 1) / 3) * sub + sub / 2f;
                    float baseline = ny - (noteText.descent() + noteText.ascent()) / 2f;
                    canvas.drawText(String.valueOf(d), nx, baseline, noteText);
                }
            }
        }

        for (int k = 1; k < 9; k++) {
            if (k % 3 == 0) continue;
            float off = k * cell;
            canvas.drawLine(originX + off, originY, originX + off, originY + size, thinLine);
            canvas.drawLine(originX, originY + off, originX + size, originY + off, thinLine);
        }
        for (int k = 0; k <= 9; k += 3) {
            float off = k * cell;
            canvas.drawLine(originX + off, originY, originX + off, originY + size, thickLine);
            canvas.drawLine(originX, originY + off, originX + size, originY + off, thickLine);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            int c = (int) Math.floor((e.getX() - originX) / cell);
            int r = (int) Math.floor((e.getY() - originY) / cell);
            if (r >= 0 && r < 9 && c >= 0 && c < 9) {
                int idx = r * 9 + c;
                if (idx != selected && listener != null) {
                    if (action == MotionEvent.ACTION_DOWN) {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    }
                    listener.onCellSelected(idx);
                }
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            performClick();
            return true;
        }
        return super.onTouchEvent(e);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void updateContentDescription() {
        if (selected < 0) {
            setContentDescription(getContext().getString(R.string.board_description));
            return;
        }
        int v = values[selected];
        String content = v == 0 ? "empty" : String.valueOf(v) + (givens[selected] != 0 ? ", given" : "");
        setContentDescription("Row " + (selected / 9 + 1) + ", column " + (selected % 9 + 1) + ": " + content);
    }
}
