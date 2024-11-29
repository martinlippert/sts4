/*******************************************************************************
 * Copyright (c) 2024 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.annotations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FileASTRequestor;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJava;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;

public class AnnotationTypeCollectorTest {
	
	private List<Path> createdFiles = new ArrayList<>();
	
	@AfterEach
	void tearDown() {
		clearTestFiles();
	}
	
	@Test
	void testSimpleHierarchy() throws Exception {
		String projectName = "test-spring-validations";
		IJavaProject project = ProjectsHarness.INSTANCE.mavenProject(projectName);
		Path file = createFile(projectName, "test", "MyComponent.java", """
		package test;
		
		import org.springframework.boot.autoconfigure.SpringBootApplication;
		
		@SpringBootApplication
		public class MyComponent {
		
		}
		""");
		
		AnnotationTypeCollector collector = new AnnotationTypeCollector();

		SpringIndexerJava.createParser(project, true).createASTs(new String[] { file.toFile().toString() }, null, new String[0], new FileASTRequestor() {
			@Override
			public void acceptAST(String sourceFilePath, CompilationUnit cu) {
				cu.accept(new ASTVisitor() {

					@Override
					public boolean visit(MarkerAnnotation node) {
						ITypeBinding binding = node.resolveTypeBinding();
						assertNotNull(binding);
						assertEquals("org.springframework.boot.autoconfigure.SpringBootApplication", binding.getQualifiedName());
						
						assertFalse(collector.inherits(node, "test.CustomComponent2", false));
						assertTrue(collector.inherits(node, "org.springframework.context.annotation.Configuration", false));
						assertTrue(collector.inherits(node, "org.springframework.boot.autoconfigure.SpringBootApplication", false));
						assertFalse(collector.inherits(node, "org.springframework.boot.autoconfigure.SpringBootApplication", true));
						assertTrue(collector.inherits(node, "org.springframework.stereotype.Component", false));
						assertTrue(collector.inherits(node, "org.springframework.stereotype.Indexed", false));
						assertFalse(collector.inherits(node, "java.lang.annotation.Documented", false));
						assertFalse(collector.inherits(node, "java.lang.annotation.Target", false));
						
						return super.visit(node);
					}
					
				});
			}	
		}, null);
	}

	@Test
	void testCircularAnnotations() throws Exception {
		String projectName = "test-spring-validations";
		IJavaProject project = ProjectsHarness.INSTANCE.mavenProject(projectName);

		createFile(projectName, "test", "CustomComponent1.java", """
		package test;
		
		import org.springframework.stereotype.Component;
		
		@Component
		@CustomComponent2
		public @interface CustomComponent1 {
		
		}
		""");

		createFile(projectName, "test", "CustomComponent2.java", """
		package test;
		
		import org.springframework.stereotype.Component;
		
		@Component
		@CustomComponent1
		public @interface CustomComponent2 {
		
		}
		""");

		Path file = createFile(projectName, "test", "MyComponent.java", """
		package test;
		
		@CustomComponent1
		public class MyComponent {
		
		}
		""");
		
		AnnotationTypeCollector collector = new AnnotationTypeCollector();
		
		SpringIndexerJava.createParser(project, true).createASTs(new String[] { file.toFile().toString() }, null, new String[0], new FileASTRequestor() {
			@Override
			public void acceptAST(String sourceFilePath, CompilationUnit cu) {
				cu.accept(new ASTVisitor() {

					@Override
					public boolean visit(MarkerAnnotation node) {
						ITypeBinding binding = node.resolveTypeBinding();
						assertNotNull(binding);
						assertEquals("test.CustomComponent1", binding.getQualifiedName());
						
						assertTrue(collector.inherits(node, "test.CustomComponent1", false));
						assertFalse(collector.inherits(node, "test.CustomComponent1", true));
						
						assertTrue(collector.inherits(node, "test.CustomComponent2", false));
						assertTrue(collector.inherits(node, "org.springframework.stereotype.Component", false));
						assertFalse(collector.inherits(node, "org.springframework.context.annotation.Configuration", false));
						
						return super.visit(node);
					}
					
				});
				
			}
			
		}, null);
	}
	
	@Test
	void testSimpleTypeDeclaration() throws Exception {
		String projectName = "test-spring-validations";
		IJavaProject project = ProjectsHarness.INSTANCE.mavenProject(projectName);
		Path file = createFile(projectName, "test", "MyComponent.java", """
		package test;
		
		import org.springframework.boot.autoconfigure.SpringBootApplication;
		
		@SpringBootApplication
		public class MyComponent {
		
		}
		""");
		
		AnnotationTypeCollector collector = new AnnotationTypeCollector();

		SpringIndexerJava.createParser(project, true).createASTs(new String[] { file.toFile().toString() }, null, new String[0], new FileASTRequestor() {
			@Override
			public void acceptAST(String sourceFilePath, CompilationUnit cu) {
				cu.accept(new ASTVisitor() {

					@Override
					public boolean visit(TypeDeclaration node) {
						String qualifiedName = node.resolveBinding().getQualifiedName();
						assertEquals("test.MyComponent", qualifiedName);
						
						assertTrue(collector.isAnnotatedWith(node, "org.springframework.boot.autoconfigure.SpringBootApplication"));
						assertTrue(collector.isAnnotatedWith(node, "org.springframework.context.annotation.Configuration"));
						assertTrue(collector.isAnnotatedWith(node, "org.springframework.stereotype.Component"));
						assertFalse(collector.isAnnotatedWith(node, "org.springframework.stereotype.Service"));
						
						return super.visit(node);
					}
					

					
				});
			}	
		}, null);
	}

	@Test
	void testAnnotationsHierarchy() throws Exception {
		String projectName = "test-spring-validations";
		IJavaProject project = ProjectsHarness.INSTANCE.mavenProject(projectName);
		Path file = createFile(projectName, "test", "MyComponent.java", """
		package test;
		
		import org.springframework.boot.autoconfigure.SpringBootApplication;
		
		@SpringBootApplication
		public class MyComponent {
		
		}
		""");
		
		AnnotationTypeCollector collector = new AnnotationTypeCollector();

		SpringIndexerJava.createParser(project, true).createASTs(new String[] { file.toFile().toString() }, null, new String[0], new FileASTRequestor() {
			@Override
			public void acceptAST(String sourceFilePath, CompilationUnit cu) {
				cu.accept(new ASTVisitor() {

					@Override
					public boolean visit(MarkerAnnotation node) {
						ITypeBinding binding = node.resolveTypeBinding();
						assertNotNull(binding);
						assertEquals("org.springframework.boot.autoconfigure.SpringBootApplication", binding.getQualifiedName());
						
						String[] annotationHierarchy = collector.getAnnotationHierarchy(node, true);
						assertEquals(8, annotationHierarchy.length);
						
						assertEquals("org.springframework.boot.SpringBootConfiguration", annotationHierarchy[0]);
						assertEquals("org.springframework.context.annotation.Configuration", annotationHierarchy[1]);
						assertEquals("org.springframework.stereotype.Component", annotationHierarchy[2]);
						assertEquals("org.springframework.stereotype.Indexed", annotationHierarchy[3]);
						assertEquals("org.springframework.boot.autoconfigure.EnableAutoConfiguration", annotationHierarchy[4]);
						assertEquals("org.springframework.boot.autoconfigure.AutoConfigurationPackage", annotationHierarchy[5]);
						assertEquals("org.springframework.context.annotation.Import", annotationHierarchy[6]);
						assertEquals("org.springframework.context.annotation.ComponentScan", annotationHierarchy[7]);
						
						return super.visit(node);
					}
					
				});
			}	
		}, null);
	}

	private Path createFile(String projectName, String packageName, String name, String content) throws Exception {
		Path projectPath = Paths.get(getClass().getResource("/test-projects/" + projectName).toURI());
		Path filePath = projectPath.resolve("src/main/java").resolve(packageName.replace('.', '/')).resolve(name);
		Files.createDirectories(filePath.getParent());
		createdFiles.add(Files.createFile(filePath));
		Files.write(filePath, content.getBytes(StandardCharsets.UTF_8));
		return filePath;
	}
	
	private void clearTestFiles() {
		for (Iterator<Path> itr = createdFiles.iterator(); itr.hasNext();) {
			Path path = itr.next();
			try {
				Files.delete(path);
				itr.remove();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

}
