



package fr.whimtrip.ext.jwhthtmltopojo.adapter;

import fr.whimtrip.core.util.WhimtripUtils;
import fr.whimtrip.core.util.exception.ObjectCreationException;
import fr.whimtrip.ext.jwhthtmltopojo.HtmlToPojoEngine;
import fr.whimtrip.ext.jwhthtmltopojo.HtmlToPojoUtils;
import fr.whimtrip.ext.jwhthtmltopojo.annotation.*;
import fr.whimtrip.ext.jwhthtmltopojo.exception.HtmlToPojoException;
import fr.whimtrip.ext.jwhthtmltopojo.intrf.HtmlAdapter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * <p>Part of project jwht-htmltopojo</p>
 *
 * <p>Default implementation of an HtmlAdapter.</p>
 *
 *
 * @param <T> the type this adapter will convert any HTML string to.
 * @author Louis-wht
 * @since 1.0.0
 */
public class DefaultHtmlAdapterImpl<T> implements HtmlAdapter<T> {


    protected HtmlToPojoEngine htmlToPojoEngine;
    protected Class<T> clazz;
    protected Map<String, AbstractHtmlFieldImpl<T>> htmlFieldCache;


    protected List<HtmlToPojoAnnotationMap<Inject>> injectFields = new ArrayList<>();
    protected List<HtmlToPojoAnnotationMap<Injected>> injectedFields = new ArrayList<>();
    protected List<HtmlToPojoAnnotationMap<InjectParent>> injectParentFields = new ArrayList<>();


    protected Map<Class<?>, List> annotatedFields =  new LinkedHashMap<>();


    /**
     * This constructor will parse the given clazz using java reflection in order
     * to target all fields and add them to the corresponding lists of annotated
     * elements so that they can be later easily retrieved.
     *
     * @param htmlToPojoEngine the engine that will instanciate other {@link HtmlAdapter}
     *                         for inner fields of POJO or fields of list of POJO.
     * @param clazz the clazz to parse and to map HTML string to.
     */
    public DefaultHtmlAdapterImpl(HtmlToPojoEngine htmlToPojoEngine, Class<T> clazz) {


        this.htmlToPojoEngine = htmlToPojoEngine;
        this.clazz = clazz;
        htmlFieldCache = new LinkedHashMap<>();

        annotatedFields.put(Injected.class, injectedFields);
        annotatedFields.put(Inject.class, injectFields);
        annotatedFields.put(InjectParent.class, injectParentFields);

        Field[] declaredFields = clazz.getDeclaredFields();
        for (Field field : declaredFields) {
            Class<?> fieldClass = field.getType();

            // Annotated field
            Selector selector = field.getAnnotation(Selector.class);

            // Not annotated field of annotated class
            if (selector == null) {
                selector = fieldClass.getAnnotation(Selector.class);
            }

            // Not annotated field - List of annotated type
            if (selector == null && List.class.isAssignableFrom(fieldClass)) {
                selector = getSelectorFromListType(field);
            }

            if (selector != null) {
                addCachedHtmlField(field, selector, fieldClass);
            }

            addAnnotatedField(field);

        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public <U> List<HtmlToPojoAnnotationMap<U>> getFieldList(Class<? extends U> clazz)
    {
        return (List<HtmlToPojoAnnotationMap<U>>) annotatedFields.get(clazz);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <U> HtmlToPojoAnnotationMap<U> getfield(String name, Class<? extends U> clazz)
    {
        List<HtmlToPojoAnnotationMap<U>> htmlToPojoAnnotationMaps = getFieldList(clazz);
        for(HtmlToPojoAnnotationMap<U> htmlToPojoAnnotationMap :  htmlToPojoAnnotationMaps)
        {
            if(htmlToPojoAnnotationMap.getName().equals(name))
                return htmlToPojoAnnotationMap;
        }
        return null;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public T fromHtml(String htmlContent) throws HtmlToPojoException {
        Element pageRoot = Jsoup.parse(htmlContent);
        return loadFromNode(pageRoot);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T fromHtml(String htmlContent, T obj) throws HtmlToPojoException {
        Element pageRoot = Jsoup.parse(htmlContent);
        return loadFromNode(pageRoot, obj);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <M> T createNewInstance(M parentObj) {
        return buildInstance((T) WhimtripUtils.createNewInstance(clazz), parentObj);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public T loadFromNode(Element node) throws HtmlToPojoException {
        return loadFromNode(node, createNewInstance());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T loadFromNode(Element node, T newInstance) throws HtmlToPojoException {
        for (AbstractHtmlFieldImpl<T> htmlField : htmlFieldCache.values()) {
            htmlField.setValue(htmlToPojoEngine, node, newInstance);
        }
        return newInstance;
    }

    /**
     * Inner method to create a new instance T.
     * @return the new instance T.
     */
    protected T createNewInstance() {
        return WhimtripUtils.createNewInstance(clazz);
    }



    /**
     * This method will parse all annotations of a given field and put map this field
     * to several {@link HtmlToPojoAnnotationMap} that will then be added to
     * {@code annotatedFields} so that they can be easily retrieved and used later on.
     *
     * @param field the field to parse annotations for.
     * @param <U> the type of each annotation built with type inference in the for loop.
     */
    @SuppressWarnings("unchecked")
    private <U> void addAnnotatedField(final Field field)
    {

        Annotation[] annotations = field.getAnnotations();
        for (Annotation a : annotations)
        {
            U annotation = (U) a;

            List<HtmlToPojoAnnotationMap<U>> fieldsConcerned = (List<HtmlToPojoAnnotationMap<U>>) annotatedFields.get(a.annotationType());
            if(fieldsConcerned == null)
            {
                fieldsConcerned = new ArrayList<>();
                annotatedFields.put(a.annotationType(), fieldsConcerned);
            }


            HtmlToPojoAnnotationMap<U> htmlToPojoAnnotationMap = new HtmlToPojoAnnotationMap<U>();
            htmlToPojoAnnotationMap.setAnnotation(annotation);
            htmlToPojoAnnotationMap.setField(field);
            htmlToPojoAnnotationMap.setName(field.getName());

            fieldsConcerned.add(htmlToPojoAnnotationMap);

        }
    }


    /**
     * Given a List typed field, this method has to find the {@link Selector} annotation
     * on top of the corresponding POJO class.
     *
     * @param field the list typed field to analyse.
     * @return the {@link Selector} annotation retrieved on the corresponding POJO class.
     */
    private Selector getSelectorFromListType(Field field) {
        Type genericType = field.getGenericType();
        Class<?> listClass = (Class<?>) ((ParameterizedType) genericType).getActualTypeArguments()[0];
        return listClass.getAnnotation(Selector.class);
    }

    /**
     * Add a parsed HtmlField to the list of cached fields. An HTML field is a processing unit
     * that is able to assign a value to the corresponding field of a POJO given the raw node
     * of the parent POJO. This processing unit is further implemented by 3 other classes depending
     * on the type of field to parse.
     *
     * @param field the raw java field we want to build an HTML Field processing unit for.
     * @param selector the {@link Selector} for this field.
     * @param fieldClass The class of the field.
     */
    private void addCachedHtmlField(Field field, Selector selector, Class<?> fieldClass) {

        AbstractHtmlFieldImpl<T> htmlField;

        if (List.class.isAssignableFrom(fieldClass))
            htmlField = new HtmlListField<>(field, selector);

        else if ((selector.postConvert() && selector.useDeserializer()) || HtmlToPojoUtils.isSimple(fieldClass))
            htmlField = new HtmlSimpleField<>(field, selector);

        else
            htmlField = new HtmlClassField<>(field, selector);

        htmlFieldCache.put(field.getName(), htmlField);
    }


    /**
     * This method will perform injection of parent object field values
     * into children object values. This can be really useful if some fields
     * of your POJO presents particular processing unit that needs to use
     * those injected values to perform the corresponding processing.
     *
     * @param newInstance the instance to perform injection to.
     * @param parentObj the parent object to take fields to inject from.
     * @param <M>  parent object type.
     * @param <R> inferred type of objects to be injected.
     * @return the same instance T as the one submitted in the parameters.
     */
    private <M, R> T buildInstance(T newInstance, M parentObj) {
        for(HtmlToPojoAnnotationMap<Inject> injector : injectFields)
        {
            List<HtmlToPojoAnnotationMap<Injected>> injectedFields = htmlToPojoEngine
                    .adapter(parentObj.getClass())
                    .getFieldList(Injected.class);

            HtmlToPojoAnnotationMap<Injected> injected = null;

            for(HtmlToPojoAnnotationMap<Injected> injectedField : injectedFields)
            {
                if(injectedField.getAnnotation().value().equals(injector.getAnnotation().value()))
                    injected = injectedField;
            }


            List<HtmlToPojoAnnotationMap<InjectParent>> injectParentFields = htmlToPojoEngine
                    .adapter(newInstance.getClass())
                    .getFieldList(InjectParent.class);

            for(HtmlToPojoAnnotationMap<InjectParent> injectParent : injectParentFields)
            {
                try {
                    WhimtripUtils.setObjectToField(injectParent.getField(), newInstance, parentObj);
                }
                catch(IllegalAccessException e)
                {
                    e.printStackTrace();
                }
            }

            if(injected == null)
                throw new ObjectCreationException(
                        ". field with name " + injector.getAnnotation().value() + " wasn't found on model "
                                + parentObj.getClass() + " while creating model of class " + newInstance.getClass()
                );

            try {
                R toBeInjectedObj = WhimtripUtils.getObjectFromField(injected.getField(), parentObj);
                WhimtripUtils.setObjectToField(injector.getField(), newInstance, toBeInjectedObj);
            }
            catch(IllegalAccessException e)
            {
                throw new ObjectCreationException(e);
            }

        }
        return newInstance;
    }


}
