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
            String name = create.getTable().getName();

            List<ColumnDefinition> columns = create.getColumnDefinitions();

            writer.write("# " + name + "\r\n");
            writer.write("\r\n");
            writer.write("| Name | Type | Spec |\r\n");
            writer.write("|---|---|---|\n");
            for (ColumnDefinition def : columns) {
                writer.write("| "
                        + def.getColumnName() + " | "
                        + def.getColDataType() + " | "
                        + def.toStringDataTypeAndSpec().substring(def.getColDataType().toString().length() + 1)
                        + " |\r\n");
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

