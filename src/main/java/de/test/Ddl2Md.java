package de.test;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserManager;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.create.table.ColumnDefinition;
import net.sf.jsqlparser.statement.create.table.CreateTable;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.Writer;
import java.util.List;

public class Ddl2Md {
    public static void main(String[] args) throws Exception {
        if (args == null || args.length < 2) {
            System.out.println("arguments missing <ddlfile> <out>");
            System.exit(1);
        }

        System.out.println("reading ddl file " + args[0] + ", writing to " + args[1]);
        String[] lines = readSql(args[0]);
        System.out.println("I found " + lines.length + " statements");

        int i = 0;
        int failedCnt = 0;

        try (FileWriter writer = new FileWriter(args[1])) {

            for (String s : lines) {
                System.out.println(++i + ": " + (s.length() > 100 ? s.substring(0, 100) + "..." : s));

                try {
                    handleTable(s, writer);
                } catch (Exception e) {
                    failedCnt++;
                    System.out.println("ddl parsing failed in " + i + "/" + lines.length);
                }
            }
        }

        System.out.println("processed " + i + " ddl statements, failed " + failedCnt);
    }

    private static void handleTable(String tableDdl, Writer writer) throws JSQLParserException, IOException {
        CCJSqlParserManager pm = new CCJSqlParserManager();

        writer.write("\r\n");

        Statement statement = pm.parse(new StringReader(tableDdl));

        if (statement instanceof CreateTable) {

            CreateTable create = (CreateTable) statement;
            List<ColumnDefinition> columns = create.getColumnDefinitions();

            writer.write("# " + create.getTable().getName() + "\r\n");

            List<String> to = create.getTableOptionsStrings();
            if (to.indexOf("COMMENT") > 0) {
                String c = to.get(to.indexOf("COMMENT") + 2);
                if (c.startsWith("'") && c.endsWith("'")) c = c.substring(1, c.length() - 1);
                writer.write("\r\n" + c + "\r\n");
            }


            boolean foundComment = false;
            for (ColumnDefinition def : columns) {
                if (def.toStringDataTypeAndSpec().indexOf("COMMENT") > 0) {
                    foundComment = true;
                    break;
                }
            }

            writer.write("\r\n");
            writer.write("| Name | Type | Spec |"
                    + (foundComment ? " Kommentar |" : "")
                    + "\r\n");
            writer.write("|---|---|---|"
                    + (foundComment ? "---|" : "")
                    + "\n");

            for (ColumnDefinition def : columns) {
                String t = def.toStringDataTypeAndSpec().substring(def.getColDataType().toString().length() + 1)
                        .replace("COLLATE utf8mb4_bin", "")
                        .trim();

                String comment = "";
                if (t.indexOf("COMMENT") > 0) {
                    comment = t.substring(t.indexOf("COMMENT") + 7).trim();

                    t = t.substring(0, t.indexOf("COMMENT")).trim();
                    if (comment.startsWith("'")) comment = comment.substring(1);
                    if (comment.endsWith("'")) comment = comment.substring(0, comment.length() - 1).trim();
                }

                writer.write("| "
                        + def.getColumnName() + " | "
                        + def.getColDataType() + " | "
                        + t
                        + " |"
                        + (foundComment ? " " + comment + " |" : "")
                        + "\r\n");
            }
            writer.write("\r\n\r\n");
        }
    }

    public static String[] readSql(String file) throws IOException {
        StringBuilder mysql = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().startsWith("--")
                        || line.trim().length() == 0) continue;

                mysql.append(line
                        .replaceAll("[\u01ff-\uffff]", "") // remove strange characters
                        .replaceAll("`", "")
                );

                if (line.matches(".*;$")) {
                    mysql.append("\n");
                }
            }
        }

        return mysql.toString().split(";[\r\n]");
    }
}

