package org.grovecity.drizzlesms;

import org.grovecity.drizzlesms.util.CharacterCalculator;

public class TransportOption {

  public enum Type {
    SMS,
    TEXTSECURE
  }

  private int                 drawable;
  private String              text;
  private Type                type;
  private String              composeHint;
  private CharacterCalculator characterCalculator;

  public TransportOption(Type type,
                         int drawable,
                         String text,
                         String composeHint,
                         CharacterCalculator characterCalculator)
  {
    this.type                = type;
    this.drawable            = drawable;
    this.text                = text;
    this.composeHint         = composeHint;
    this.characterCalculator = characterCalculator;
  }

  public Type getType() {
    return type;
  }

  public boolean isType(Type type) {
    return this.type == type;
  }

  public boolean isPlaintext() {
    return type == Type.SMS;
  }

  public boolean isSms() {
    return type == Type.SMS;
  }

  public CharacterCalculator.CharacterState calculateCharacters(int charactersSpent) {
    return characterCalculator.calculateCharacters(charactersSpent);
  }

  public int getDrawable() {
    return drawable;
  }

  public String getComposeHint() {
    return composeHint;
  }

  public String getDescription() {
    return text;
  }
}
