package org.grovecity.drizzlesms.components.emoji;

public interface EmojiPageModel {
  int getIconRes();
  int[] getCodePoints();
  boolean isDynamic();
}
