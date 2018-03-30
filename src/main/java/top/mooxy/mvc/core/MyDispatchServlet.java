package top.mooxy.mvc.core;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import top.mooxy.mvc.annotation.MyController;
import top.mooxy.mvc.annotation.MyRequestMapping;

public class MyDispatchServlet extends HttpServlet{
	
	Properties properties = new Properties();
	
	private List<String> classNames = new ArrayList<>();
	
	private Map<String, Object> ioc = new HashMap<>();
	
	private Map<String, Method> handlerMapping = new  HashMap<>();
	
	private Map<String, Object> controllerMap  =new HashMap<>();

	
	@Override
	public void init(ServletConfig config) throws ServletException {
		
		System.out.println("【系统初始化】：开始");
		
		System.out.println("【加载配置文件】:--------------------------------------------");
		//1.加载配置文件
		doLoadConfig(config.getInitParameter("contextConfigLocation"));
		System.out.println("【扫描相关类】:--------------------------------------------");
		//2.初始化所有相关联的类，扫描用户设定的包下面所有的类
		doScanner((String)properties.get("scanPackage"));
		System.out.println("【初始化相关类】:--------------------------------------------");
		//3.拿到扫描到的类,通过反射机制,实例化,并且放到ioc容器中(k-v  beanName-bean) beanName默认是首字母小写
		doInstance();
	    //4.初始化HandlerMapping(将url和method对应上)
		System.out.println("【初始化mapping】:--------------------------------------------");
	    initHandlerMapping();

	    System.out.println("【系统初始化】：结束");
	}
	

	private void initHandlerMapping() {
		
		if(ioc.isEmpty()){
			return;
		}
		
		try {
			for (Entry<String, Object> entry: ioc.entrySet()) {
				
					Class<? extends Object> clazz = entry.getValue().getClass();
				
					if(!clazz.isAnnotationPresent(MyController.class)){
						continue;
					}
					
						//拼url时,是controller头的url拼上方法上的url
					String baseUrl ="";
						
					if(clazz.isAnnotationPresent(MyRequestMapping.class)){
						
						MyRequestMapping annotation = clazz.getAnnotation(MyRequestMapping.class);
					
						baseUrl=annotation.value();
					}
						
					Method[] methods = clazz.getMethods();
					
					for (Method method : methods) {
					
							if(!method.isAnnotationPresent(MyRequestMapping.class)){
								
							continue;
				    }
						
				
				   MyRequestMapping annotation = method.getAnnotation(MyRequestMapping.class);
						
				   String url = annotation.value();
				
				   url =(baseUrl+"/"+url).replaceAll("/+", "/");
				   
				   handlerMapping.put(url,method);
				
				   controllerMap.put(url,clazz.newInstance());
				
				   System.out.println("【mapping绑定：】"+url+","+method);
					
				}
			}
		} catch (Exception e) {
					e.printStackTrace();
		}
	}

	private void doInstance() {
		if (classNames.isEmpty()) {
			return;
		}
		for (String className : classNames) {
			try {
			//把类搞出来,反射来实例化(只有加@MyController需要实例化)
			Class<?> clazz =Class.forName(className);
			if(clazz.isAnnotationPresent(MyController.class)){
					 
					   Object newInstance = clazz.newInstance();
					  
					   String simpleName = clazz.getSimpleName().toLowerCase();
					  
					   System.out.println(simpleName+"【类实例化：】 "+className);
					  
					   ioc.put(simpleName,newInstance);
				}else{
					continue;
				}
			} catch (Exception e) {
					e.printStackTrace();
					continue;
			}
		}
	}

	private void doScanner(String packageName) {
		
		URL url = this.getClass().getClassLoader().getResource("/"+packageName.replaceAll("\\.", "/"));
		
		File dir = new File(url.getFile());

		for (File file : dir.listFiles()) {
			if(file.isDirectory()){
				//递归读取包
				doScanner(packageName+"."+file.getName());
			}else{
				String className =packageName +"." +file.getName().replace(".class", "");
				System.out.println("【包扫描加载：】 "+className);
				classNames.add(className);
			}
		}
	}

	@Override
	public void destroy() {
		System.out.println("【系统销毁】");
	}



	private void doLoadConfig(String location) {
		
		if(location.contains("classpath:")){
			
			location = location.substring(location.indexOf(":")+1, location.length());
			
		}
		
		InputStream resourceAsStream = this.getClass().getClassLoader().getResourceAsStream(location);
		
		try {
			
			properties.load(resourceAsStream);
			
		} catch (IOException e) {
			
			e.printStackTrace();
		} finally{
			if(resourceAsStream!=null){
				try {
					resourceAsStream.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}


	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		try {
			//处理请求
			doDispatch(req, resp);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}


	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		try {
			//处理请求
			doDispatch(req, resp);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}


	private void doDispatch(HttpServletRequest request, HttpServletResponse response) throws Exception{
		
	    if(handlerMapping.isEmpty()){
	        return;
	    }
		
	    String url =request.getRequestURI();
	    
	    String contextPath = request.getContextPath();
	   
	    url=url.replace(contextPath, "").replaceAll("/+", "/");
	    
	    if(!this.handlerMapping.containsKey(url)){
	    	
	    	response.getWriter().write("404 NOT FOUND!");
			
	    	return;
	    }
	    
	    Method method =this.handlerMapping.get(url);
	    //获取方法的参数列表
	    Class<?>[] parameterTypes = method.getParameterTypes();
	    //获取请求的参数
	    Map<String, String[]> parameterMap = request.getParameterMap();
	    
	    //保存参数值
	    Object[] paramValues = new Object[parameterTypes.length];
	    
	    //方法测参数列表
	    
	    for (int i = 0; i < parameterTypes.length; i++) {
			
	    	//根据参数名做相关处理
	    	String requestParam = parameterTypes[i].getSimpleName();  
	    	
		            if (requestParam.equals("HttpServletRequest")){  
		                //参数类型已明确，这边强转类型  
		            		paramValues[i]=request;
		            		continue;  
		            }  
		            if (requestParam.equals("HttpServletResponse")){  
		            		paramValues[i]=response;
		            		continue;  
		            }
		            if(requestParam.equals("String")){
		            	for (Entry<String, String[]> param : parameterMap.entrySet()) {
		            		String value =Arrays.toString(param.getValue()).replaceAll("\\[|\\]", "").replaceAll(",\\s", ",");
		            		paramValues[i]=value;
		            	}
		            }
		}
	    
	    //利用反射机制来调用
	    try {
	       method.invoke(this.controllerMap.get(url), paramValues);//obj是method所对应的实例 在ioc容器中
	    } catch (Exception e) {
	        e.printStackTrace();
	    }
	}
}
