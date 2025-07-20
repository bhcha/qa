package com.ldx.qa.util;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Markdown 문법을 HTML로 변환하는 유틸리티 클래스
 */
public class MarkdownToHtmlConverter {
    
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,4})\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern ITALIC_PATTERN = Pattern.compile("\\*([^*]+?)\\*");
    private static final Pattern INLINE_CODE_PATTERN = Pattern.compile("`([^`]+?)`");
    private static final Pattern UNORDERED_LIST_PATTERN = Pattern.compile("^\\s*[-*+]\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern ORDERED_LIST_PATTERN = Pattern.compile("^\\s*\\d+\\.\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern HORIZONTAL_RULE_PATTERN = Pattern.compile("^-{3,}$", Pattern.MULTILINE);
    private static final Pattern SEPARATOR_PATTERN = Pattern.compile("={3,}");
    
    /**
     * Markdown 텍스트를 HTML로 변환
     */
    public static String convertToHtml(String markdown) {
        if (markdown == null || markdown.trim().isEmpty()) {
            return "";
        }
        
        String html = markdown;
        
        // 1. 헤딩 변환 (가장 먼저 처리)
        html = convertHeadings(html);
        
        // 2. 강조 텍스트 변환
        html = convertBold(html);
        html = convertItalic(html);
        
        // 3. 인라인 코드 변환
        html = convertInlineCode(html);
        
        // 4. 리스트 변환
        html = convertUnorderedLists(html);
        html = convertOrderedLists(html);
        
        // 5. 구분선 변환
        html = convertHorizontalRules(html);
        html = convertSeparators(html);
        
        // 6. 단락 변환 (마지막에 처리)
        html = convertParagraphs(html);
        
        return html;
    }
    
    /**
     * 헤딩 변환: # ## ### #### → <h1> <h2> <h3> <h4>
     */
    private static String convertHeadings(String text) {
        Matcher matcher = HEADING_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        
        while (matcher.find()) {
            String hashes = matcher.group(1);
            String content = matcher.group(2);
            int level = hashes.length();
            
            String replacement = String.format("<h%d>%s</h%d>", level, content.trim(), level);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        
        return sb.toString();
    }
    
    /**
     * 굵은 글씨 변환: **text** → <strong>text</strong>
     */
    private static String convertBold(String text) {
        return BOLD_PATTERN.matcher(text).replaceAll("<strong>$1</strong>");
    }
    
    /**
     * 기울임 글씨 변환: *text* → <em>text</em>
     */
    private static String convertItalic(String text) {
        return ITALIC_PATTERN.matcher(text).replaceAll("<em>$1</em>");
    }
    
    /**
     * 인라인 코드 변환: `code` → <code>code</code>
     */
    private static String convertInlineCode(String text) {
        return INLINE_CODE_PATTERN.matcher(text).replaceAll("<code>$1</code>");
    }
    
    /**
     * 순서 없는 리스트 변환: - item → <ul><li>item</li></ul>
     */
    private static String convertUnorderedLists(String text) {
        String[] lines = text.split("\n");
        StringBuilder result = new StringBuilder();
        boolean inList = false;
        
        for (String line : lines) {
            Matcher matcher = UNORDERED_LIST_PATTERN.matcher(line);
            
            if (matcher.matches()) {
                if (!inList) {
                    result.append("<ul>\n");
                    inList = true;
                }
                result.append("  <li>").append(matcher.group(1).trim()).append("</li>\n");
            } else {
                if (inList) {
                    result.append("</ul>\n");
                    inList = false;
                }
                result.append(line).append("\n");
            }
        }
        
        if (inList) {
            result.append("</ul>\n");
        }
        
        return result.toString();
    }
    
    /**
     * 순서 있는 리스트 변환: 1. item → <ol><li>item</li></ol>
     */
    private static String convertOrderedLists(String text) {
        String[] lines = text.split("\n");
        StringBuilder result = new StringBuilder();
        boolean inList = false;
        
        for (String line : lines) {
            Matcher matcher = ORDERED_LIST_PATTERN.matcher(line);
            
            if (matcher.matches()) {
                if (!inList) {
                    result.append("<ol>\n");
                    inList = true;
                }
                result.append("  <li>").append(matcher.group(1).trim()).append("</li>\n");
            } else {
                if (inList) {
                    result.append("</ol>\n");
                    inList = false;
                }
                result.append(line).append("\n");
            }
        }
        
        if (inList) {
            result.append("</ol>\n");
        }
        
        return result.toString();
    }
    
    /**
     * 수평선 변환: --- → <hr>
     */
    private static String convertHorizontalRules(String text) {
        return HORIZONTAL_RULE_PATTERN.matcher(text).replaceAll("<hr>");
    }
    
    /**
     * 구분선 변환: === → <hr>
     */
    private static String convertSeparators(String text) {
        return SEPARATOR_PATTERN.matcher(text).replaceAll("<hr>");
    }
    
    /**
     * 단락 변환: 빈 줄로 구분된 텍스트를 <p> 태그로 감싸기
     */
    private static String convertParagraphs(String text) {
        // 이미 HTML 태그가 있는 줄은 건드리지 않기
        String[] paragraphs = text.split("\n\n");
        StringBuilder result = new StringBuilder();
        
        for (String paragraph : paragraphs) {
            paragraph = paragraph.trim();
            if (!paragraph.isEmpty()) {
                // HTML 태그가 이미 있는지 확인
                if (paragraph.startsWith("<") || paragraph.contains("<h") || 
                    paragraph.contains("<ul>") || paragraph.contains("<ol>") || 
                    paragraph.contains("<hr>")) {
                    result.append(paragraph).append("\n\n");
                } else {
                    // 일반 텍스트만 <p> 태그로 감싸기
                    result.append("<p>").append(paragraph.replace("\n", "<br>")).append("</p>\n\n");
                }
            }
        }
        
        return result.toString().trim();
    }
    
    /**
     * 특수 문자 이스케이프 (필요시 사용)
     */
    public static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        
        return text.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&#x27;");
    }
    
    /**
     * 간단한 테스트를 위한 메서드
     */
    public static void main(String[] args) {
        String markdown = "# 제목\n\n**굵은 글씨**와 *기울임*\n\n- 리스트 항목 1\n- 리스트 항목 2\n\n`코드`\n\n---\n\n일반 단락입니다.";
        System.out.println(convertToHtml(markdown));
    }
}