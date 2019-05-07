
package com.ccnode.codegenerator.pojoHelper;

import com.ccnode.codegenerator.enums.FileType;
import com.ccnode.codegenerator.enums.SupportFieldClass;
import com.ccnode.codegenerator.genCode.UserConfigService;
import com.ccnode.codegenerator.pojo.BaseResponse;
import com.ccnode.codegenerator.pojo.ChangeInfo;
import com.ccnode.codegenerator.pojo.GenCodeResponse;
import com.ccnode.codegenerator.pojo.GeneratedFile;
import com.ccnode.codegenerator.pojo.OnePojoInfo;
import com.ccnode.codegenerator.pojo.PojoFieldInfo;
import com.ccnode.codegenerator.util.GenCodeUtil;
import com.ccnode.codegenerator.util.IOUtils;
import com.ccnode.codegenerator.util.LoggerWrapper;
import com.ccnode.codegenerator.util.PojoUtil;
import com.ccnode.codegenerator.util.RegexUtil;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.PsiClassImpl;
import com.intellij.psi.impl.source.javadoc.PsiDocCommentImpl;
import com.intellij.psi.impl.source.tree.PsiCommentImpl;
import com.intellij.psi.impl.source.tree.java.PsiPackageStatementImpl;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.List;

import static org.apache.commons.io.IOUtils.writeLines;

/**
 * What always stop you is what you always believe.
 * <p>
 * Created by zhengjun.du on 2016/05/21 21:50
 */
public class OnePojoInfoHelper {

    private final static Logger LOGGER = LoggerWrapper.getLogger(OnePojoInfoHelper.class);

    @NotNull
    public static Boolean containSplitKey(@NotNull OnePojoInfo onePojoInfo, String splitKey){
        for (PojoFieldInfo pojoFieldInfo : onePojoInfo.getPojoFieldInfos()) {
            if(pojoFieldInfo.getFieldName().equalsIgnoreCase(splitKey)){
                return true;
            }
        }
        return false;
    }

    private static String removeSplit(String s){
        if(StringUtils.isBlank(s)){
            return StringUtils.EMPTY;
        }
        return s.replace("/","").replace("\\","");
    }

    public static void parseIdeaFieldInfo(@NotNull OnePojoInfo onePojoInfo, GenCodeResponse response){
        String pojoName = onePojoInfo.getPojoName();
        String pojoFileShortName = pojoName + ".java";
        Project project = response.getRequest().getProject();
        PsiFile[] psiFile = FilenameIndex
                .getFilesByName(project, pojoFileShortName, GlobalSearchScope.projectScope(project));
        PsiElement firstChild = psiFile[0].getFirstChild();
        LOGGER.info("parseIdeaFieldInfo psiFile[0] path :{}", psiFile[0].getVirtualFile().getPath());
        PsiElement child = null;
        for (PsiFile each: psiFile){
            VirtualFile vf = each.getVirtualFile();
            LOGGER.info("parseIdeaFieldInfo :{}, :{}", vf.getPath(), onePojoInfo.getFullPojoPath());
            if (removeSplit(vf.getPath()).equals(removeSplit(onePojoInfo.getFullPojoPath()))){
                child = firstChild;
            }
        }

        List<PsiElement> elements = Lists.newArrayList();

//        i// Find Psi of class and package
        do {
            if (child instanceof PsiClassImpl) {
                elements.add(child);
            }
            if (child instanceof PsiPackageStatementImpl){
                onePojoInfo.setPojoPackage(((PsiPackageStatementImpl) child).getPackageName());
            }
            child = child.getNextSibling();
        }
        while (child != null);

        PsiClassImpl psiClass = (PsiClassImpl) elements.get(0);
        PsiElement context = psiClass.getContext();
        if(context == null){
            throw new RuntimeException("parse class error");
        }
        String text = context.getText();
        onePojoInfo.setPojoPackage(parsePackage(text));
        PsiField[] allFields = psiClass.getAllFields();
        List<PojoFieldInfo> fieldList = Lists.newArrayList();

        for (PsiField field : allFields) {
            if(isStaticField(field)){
                continue;
            }
            SupportFieldClass fieldClass = SupportFieldClass.fromDesc(field.getType().getCanonicalText());
            LOGGER.info("parseIdeaFieldInfo  canonicalText :{}", field.getType().getCanonicalText());
            if(fieldClass == SupportFieldClass.NONE){
                continue;
            }
            PojoFieldInfo fieldInfo = new PojoFieldInfo();
            fieldInfo.setFieldComment(parseComment(field));
            fieldInfo.setFieldName(field.getName());
            fieldInfo.setFieldClass(fieldClass);
            fieldInfo.setAnnotations(Lists.newArrayList());
            if(!StringUtils.containsIgnoreCase(fieldInfo.getFieldComment(), "IgnoreAutoGenerate")) {
                fieldList.add(fieldInfo);
            }
        }
        onePojoInfo.setPojoFieldInfos(fieldList);
    }

    private static Boolean isStaticField(@NotNull PsiField field){
        return field.getText().contains(" static ");
    }

    private static String parsePackage(String context){
        List<String> lines = Splitter.on("\n").trimResults().omitEmptyStrings().splitToList(context);
        for (String line : lines) {
            String match = RegexUtil.getMatch("[\\s]*package[\\s]+.+[\\s]*;", line);
            if(StringUtils.isNotBlank(match)){
                String pojoPackage = Splitter.on("package").trimResults().omitEmptyStrings().splitToList(match).get(0);
                pojoPackage = pojoPackage.replace(";","");
                pojoPackage = pojoPackage.replace(" ","");
                return pojoPackage;
            }
        }
        return StringUtils.EMPTY;
    }
    private static String parseComment(PsiField field) {
        if(field == null){
            return StringUtils.EMPTY;
        }
        PsiElement[] children = field.getChildren();
        for (PsiElement child : children) {
            String text = child.getText();
            if(child instanceof PsiDocCommentImpl || child instanceof PsiCommentImpl){
                return formatText(text);
            }
        }
        return StringUtils.EMPTY;
    }

    private static String formatText(String text){
        text = text.replace("/*","");
        text = text.replace("*/","");
        text = text.replace("//","");
        text = text.replace("\n","");
        text = text.replace("*","");
        text = text.trim();
        return text;
    }

    public static void parsePojoFieldInfo(@NotNull OnePojoInfo onePojoInfo){
        @NotNull Class pojoClass = onePojoInfo.getPojoClass();
        Field[] fields = pojoClass.getDeclaredFields();
        if(fields == null || fields.length == 0){
            return;
        }
        List<PojoFieldInfo> fieldInfoList = Lists.newArrayList();
        onePojoInfo.setPojoFieldInfos(fieldInfoList);
        for (Field field : fields) {
            PojoFieldInfo fieldInfo = new PojoFieldInfo();
            fieldInfo.setFieldClass(SupportFieldClass.NONE);
            fieldInfo.setFieldName(field.getName());
            fieldInfo.setAnnotations(Lists.newArrayList(field.getDeclaredAnnotations()));
            fieldInfoList.add(fieldInfo);
        }
    }

    public static void deduceDaoPackage(OnePojoInfo onePojoInfo, GenCodeResponse response){
        GeneratedFile daoFile = getFileByType(onePojoInfo, FileType.DAO);
        if(daoFile == null){
            return;
        }
        String daoModulePath = UserConfigService.removeStartAndEndSplitter(response.getUserConfigMap().get("dao.module.path"));
        String deducePackage = GenCodeUtil.deducePackage(onePojoInfo.getFullDaoPath() ,onePojoInfo.getPojoPackage(),onePojoInfo.getFullPojoPath(),daoModulePath);
        for (String s : daoFile.getOriginLines()) {
            if(s.trim().contains("package ")){
                deducePackage = parsePackage(s);
                break;
            }
        }
        onePojoInfo.setDaoPackage(deducePackage);
    }

    public static void deduceServicePackage(OnePojoInfo onePojoInfo, GenCodeResponse response){
        GeneratedFile serviceFile = getFileByType(onePojoInfo, FileType.SERVICE);
        if(serviceFile == null){
            return;
        }
        String serviceModulePath = UserConfigService.removeStartAndEndSplitter(response.getUserConfigMap().get("service.module.path"));
        String deducePackage = GenCodeUtil.deducePackage(onePojoInfo.getFullServicePath() ,onePojoInfo.getPojoPackage(),onePojoInfo.getFullPojoPath(), serviceModulePath);
        for (String s : serviceFile.getOriginLines()) {
            if(s.trim().contains("package ")){
                deducePackage = parsePackage(s);
                break;
            }
        }
        onePojoInfo.setServicePackage(deducePackage);
    }

    @Nullable
    public static GeneratedFile getFileByType(OnePojoInfo onePojoInfo, FileType fileType){
        for (GeneratedFile generatedFile : onePojoInfo.getFiles()) {
            if(generatedFile.getFileType() == fileType){
                return generatedFile;
            }
        }
        return null;
    }

    public static void parseFiles(OnePojoInfo onePojoInfo, GenCodeResponse response) {
        onePojoInfo.setFiles(Lists.newArrayList());
        for (FileType fileType : FileType.values()) {
            if(fileType == FileType.NONE){
                continue;
            }
            GeneratedFile file = new GeneratedFile();
            file.setFileType(fileType);
            String filePath = StringUtils.EMPTY;
            switch (fileType){
             case SQL:
                 filePath = onePojoInfo.getFullSqlPath();
                break;
            case MAPPER:
                 filePath = onePojoInfo.getFullMapperPath();
                break;
            case SERVICE:
                 filePath = onePojoInfo.getFullServicePath();
                break;
            case DAO:
                 filePath = onePojoInfo.getFullDaoPath();
                break;
            }
            file.setFilePath(filePath);
            file.setFile(new File(file.getFilePath()));
            if(!file.getFile().exists()){
             file.getFile().getParentFile().mkdirs();
                try {
                    file.getFile().createNewFile();
                } catch (Exception e) {
                    response.failure("create file : " +file.getFile().getAbsolutePath() +" failure",e);
                }
            }
            try{
                if(file.getFile().exists()){
                    file.setOldLines(Lists.newArrayList(IOUtils.readLines(file.getFile())));
                    file.setOriginLines(Lists.newArrayList(IOUtils.readLines(file.getFile())));
                }else{
                    file.setOldLines(Lists.newArrayList());
                    file.setOriginLines(Lists.newArrayList());
                }

            }catch(Exception e){
                response.failure("read" + file.getFile().getAbsolutePath() +" failure",e);
            }
            file.setNewLines(Lists.newArrayList());
            onePojoInfo.getFiles().add(file);
        }
    }

    // todo
    public static BaseResponse flushFiles(@NotNull OnePojoInfo onePojoInfo, GenCodeResponse response){

        try{
            for (GeneratedFile generatedFile : onePojoInfo.getFiles()) {
                List<String> lines = generatedFile.getNewLines();
                if(lines == null || lines.isEmpty()){
                    lines = generatedFile.getOriginLines();
                }
                writeLines(lines, "\n", new FileOutputStream(generatedFile.getFile()), Charset.forName(response.getConfig("fileCharset", "UTF-8")));
                LOGGER.info(" flushFiles, affected file :{}", generatedFile.getFile().getAbsolutePath());
            }
            Pair<List<ChangeInfo>, List<ChangeInfo>> pair = statisticChange(response.getPojoInfos());
            LOGGER.info(" flushFiles :{}, pair.getRight():{}",pair.getLeft(),pair.getRight());
            response.setNewFiles(pair.getLeft());
            response.setUpdateFiles(pair.getRight());
            return response;
        }catch(Exception e){
            LOGGER.info("flush file error",e);
            return response.failure("flush file error",e);
        }

    }

    public static Pair<List<ChangeInfo>, List<ChangeInfo>> statisticChange(List<OnePojoInfo> onePojoInfos){
        List<ChangeInfo> newFiles = Lists.newArrayList();
        List<ChangeInfo> updatedFiles = Lists.newArrayList();
        for (OnePojoInfo onePojoInfo : onePojoInfos) {
            for (GeneratedFile file : onePojoInfo.getFiles()) {
                if(file.getOriginLines().isEmpty()){
                    ChangeInfo newFile = new ChangeInfo();
                    newFile.setFileName(file.getFile().getName());
                    newFile.setAffectRow(file.getNewLines().size());
                    newFile.setChangeType("new file");
                    newFiles.add(newFile);
                }else{
                    ChangeInfo updatedFile = new ChangeInfo();
                    updatedFile.setFileName(file.getFile().getName());
                    updatedFile.setAffectRow(countChangeRows(file.getNewLines(), file.getOriginLines()));
                    updatedFile.setChangeType("updated");
                    updatedFiles.add(updatedFile);
                }
            }
        }
        return Pair.of(newFiles, updatedFiles);
        
    }

    private static Integer countChangeRows(List<String> oldLines, List<String> newLines) {
        oldLines = PojoUtil.avoidEmptyList(oldLines);
        newLines = PojoUtil.avoidEmptyList(newLines);
        Integer changeCount = 0;
        for (String oldLine : oldLines) {
            if(!newLines.contains(oldLine)){
                changeCount ++;
            }
        }

        for (String line : newLines) {
            if(!oldLines.contains(line)){
                changeCount ++;
            }
        }
        return changeCount;
    }

    public static void main(String[] args) {

        List<Integer> xList = Lists.newArrayList(0,1,2,3);
        List<Integer> yList = Lists.newArrayList(2,3,4,5,6);
        System.out.println(Sets.intersection(Sets.newHashSet(xList), Sets.newHashSet(yList)));

        String match = RegexUtil.getMatch("[\\s]*package[\\s]+.+[\\s]*;", "   package cox3m.qunar.Sn_+surance.service.dto  ;  \n");
            if(StringUtils.isNotBlank(match)){
                String pack = Splitter.on("package").trimResults().omitEmptyStrings().splitToList(match).get(0);
                pack = pack.replace(";","");
                pack = pack.replace(" ","");
            }
    }

}
