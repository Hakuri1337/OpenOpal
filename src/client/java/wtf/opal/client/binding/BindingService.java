package wtf.opal.client.binding;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.ibm.icu.impl.Pair;
import wtf.opal.client.binding.type.InputType;

import java.util.Map;
import java.util.Optional;

import static wtf.opal.client.Constants.mc;

public final class BindingService {

    private final Multimap<Pair<Integer, InputType>, IBindable> bindingMap = HashMultimap.create();

    public void register(final int code, final IBindable bindable, final InputType inputType) {
        bindingMap.put(Pair.of(code, inputType), bindable);
    }

    public void clearBindings(final IBindable bindable) {
        bindingMap.entries().removeIf(entry -> entry.getValue().equals(bindable));
    }

    public void dispatch(final int code, final InputType inputType) {
        if (mc.currentScreen != null) return;

        bindingMap.get(Pair.of(code, inputType)).forEach(IBindable::onBindingInteraction);
    }

    public Multimap<Pair<Integer, InputType>, IBindable> getBindingMap() {
        return bindingMap;
    }

    public Optional<Pair<Integer, InputType>> getKeyFromBindable(final IBindable bindable) {
        return bindingMap.entries().stream()
                .filter(entry -> entry.getValue().equals(bindable))
                .map(Map.Entry::getKey)
                .findFirst();
    }

}
