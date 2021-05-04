package org.jetbrains.research.refactorinsight.folding;

import com.intellij.codeInsight.documentation.DocumentationComponent;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBHtmlEditorKit;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import javax.swing.JEditorPane;
import javax.swing.text.Element;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.font.TextAttribute;
import java.util.HashMap;
import java.util.Map;

public class MyHintRenderer implements EditorCustomElementRenderer {
  private JEditorPane pane = null;
  private final String text;
  /**
   * Maybe it should be atomic. (Concurrent click and paint)
   */
  private Rectangle lastRect = new Rectangle();

  public MyHintRenderer(@NotNull String text) {
    this.text = text + " <a href=\"Show diff\">Show diff</a>";
  }

  @Override
  public int calcWidthInPixels(@NotNull Inlay inlay) {
    return 400;
  }

  @Override
  public int calcHeightInPixels(@NotNull Inlay inlay) {
    return inlay.getEditor().getLineHeight() + 20;
  }

  @Override
  public void paint(@NotNull Inlay inlay,
                    @NotNull Graphics graphicsGlobal,
                    @NotNull Rectangle targetRegion,
                    @NotNull TextAttributes textAttributes) {
    lastRect = new Rectangle(targetRegion.x + 10, targetRegion.y + 10,
        targetRegion.width - 20, targetRegion.height - 20);

    Graphics graphicsLocal =
        graphicsGlobal.create(targetRegion.x, targetRegion.y, targetRegion.width, targetRegion.height);

    EditorEx editor = (EditorEx) inlay.getEditor();
    graphicsLocal.setColor(editor.getColorsScheme().getColor(DefaultLanguageHighlighterColors.DOC_COMMENT_GUIDE));
    graphicsLocal.fillRect(5, 5, targetRegion.width - 10, targetRegion.height - 10);

    setupPane(inlay, targetRegion.width - 20);
    Graphics paneGraphics = graphicsLocal.create(10, 10, targetRegion.width - 20, targetRegion.height - 20);
    UISettings.setupAntialiasing(paneGraphics);
    pane.paint(paneGraphics);
    paneGraphics.dispose();
  }

  private void setupPane(@NotNull Inlay<?> inlay, int width) {
    EditorEx editor = (EditorEx) inlay.getEditor();
    if (pane == null) {
      pane = createEditorPane(editor, text);
      editor.addEditorMouseListener(new ShowDiffClickListener());
    }
    AppUIUtil.targetToDevice(pane, editor.getContentComponent());
    pane.setSize(width, 10_000_000);
  }

  /**
   * Copied from: {@link com.intellij.codeInsight.documentation.render.DocRenderer}.
   */
  @NotNull
  private static JEditorPane createEditorPane(@NotNull Editor editor, @Nls @NotNull String text) {
    JEditorPane pane = new JEditorPane();
    pane.getCaret().setSelectionVisible(true);
    // do not reserve space for caret (making content one pixel narrower than component)
    pane.putClientProperty("caretWidth", 0);
    pane.setEditorKit(new JBHtmlEditorKit());
    pane.setBorder(JBUI.Borders.empty());
    Map<TextAttribute, Object> fontAttributes = new HashMap<>();
    fontAttributes.put(TextAttribute.SIZE, JBUIScale.scale(DocumentationComponent.getQuickDocFontSize().getSize()));
    // disable kerning for now - laying out all fragments in a file with it takes too much time
    fontAttributes.put(TextAttribute.KERNING, 0);
    pane.setFont(pane.getFont().deriveFont(fontAttributes));
    Color textColor = editor.getColorsScheme().getDefaultForeground();
    pane.setForeground(textColor);
    pane.setSelectedTextColor(textColor);
    pane.setSelectionColor(editor.getSelectionModel().getTextAttributes().getBackgroundColor());
    UIUtil.enableEagerSoftWrapping(pane);
    pane.setText(text);
    return pane;
  }

  class ShowDiffClickListener implements EditorMouseListener {
    @Override
    public void mouseClicked(@NotNull EditorMouseEvent event) {
      if (event.getArea() == EditorMouseEventArea.EDITING_AREA
          && lastRect.contains(event.getMouseEvent().getPoint())) {
        Point p = new Point(event.getMouseEvent().getPoint());
        p.translate(-lastRect.x, -lastRect.y);
        var hyperlink = getHyperlinkElement(p);
        if (hyperlink != null) {
          showDiffAction();
        }
      }
    }

    @Nullable
    private Element getHyperlinkElement(Point p) {
      int pos = pane.getUI().viewToModel(pane, p);
      if (pos >= 0 && pane.getDocument() instanceof HTMLDocument) {
        HTMLDocument document = (HTMLDocument) pane.getDocument();
        Element elem = document.getCharacterElement(pos);
        if (elem.getAttributes().getAttribute(HTML.Tag.A) != null) {
          return elem;
        }
      }
      return null;
    }

    /**
     * Refactoring diff must be called here. (Maybe some more parameters needed)
     */
    private void showDiffAction() {
      System.out.println("Click");
    }
  }
}