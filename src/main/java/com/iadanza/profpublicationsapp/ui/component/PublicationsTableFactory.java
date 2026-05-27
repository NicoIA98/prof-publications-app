package com.iadanza.profpublicationsapp.ui.component;

import com.iadanza.profpublicationsapp.domain.model.Publication;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.util.function.Consumer;

/**
 * Factory per la tabella "Pubblicazioni IRIS".
 *
 * Responsabilità:
 * - costruire la TableView delle pubblicazioni;
 * - configurare colonne e pulsante BibTeX;
 * - notificare la UI principale quando cambia la selezione;
 * - mantenere fuori da ProfessorPublicationsApp la logica di costruzione tabellare.
 *
 * Nota H5:
 * la colonna Venue è stata rimossa dalla tabella per rendere la UI più pulita.
 */
public final class PublicationsTableFactory {

    private PublicationsTableFactory() {
    }

    public static TableView<Publication> create(
            ObservableList<Publication> publicationItems,
            Consumer<Publication> selectionConsumer,
            Runnable emptySelectionConsumer,
            Consumer<Publication> bibtexConsumer
    ) {
        TableView<Publication> publicationsTable = new TableView<>();
        publicationsTable.getStyleClass().add("publications-table");
        publicationsTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        publicationsTable.setItems(publicationItems);

        TableColumn<Publication, String> titleColumn = new TableColumn<>("Titolo");
        titleColumn.setCellValueFactory(cellData ->
                new ReadOnlyStringWrapper(cellData.getValue().title())
        );
        titleColumn.setPrefWidth(340);

        TableColumn<Publication, Integer> yearColumn = new TableColumn<>("Anno");
        yearColumn.setCellValueFactory(cellData ->
                new ReadOnlyObjectWrapper<>(cellData.getValue().year())
        );
        yearColumn.setPrefWidth(70);

        TableColumn<Publication, Void> bibtexColumn = new TableColumn<>("BibTeX");
        bibtexColumn.setPrefWidth(85);
        bibtexColumn.setCellFactory(param -> new TableCell<>() {
            private final Button bibtexButton = new Button(".bib");

            {
                bibtexButton.getStyleClass().add("secondary-button");
                bibtexButton.setOnAction(event -> {
                    if (getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                        return;
                    }

                    Publication publication = getTableView().getItems().get(getIndex());

                    if (bibtexConsumer != null) {
                        bibtexConsumer.accept(publication);
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);

                bibtexButton.getStyleClass().remove("bibtex-selected-button");

                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                    return;
                }

                Publication rowPublication = getTableView().getItems().get(getIndex());
                Publication selectedPublication = getTableView().getSelectionModel().getSelectedItem();

                if (rowPublication != null && rowPublication.equals(selectedPublication)) {
                    if (!bibtexButton.getStyleClass().contains("bibtex-selected-button")) {
                        bibtexButton.getStyleClass().add("bibtex-selected-button");
                    }
                }

                setGraphic(bibtexButton);
            }
        });

        TableColumn<Publication, String> doiColumn = new TableColumn<>("DOI");
        doiColumn.setCellValueFactory(cellData ->
                new ReadOnlyStringWrapper(
                        cellData.getValue().doi() != null ? cellData.getValue().doi() : "N/D"
                )
        );
        doiColumn.setPrefWidth(220);

        publicationsTable.getColumns().add(titleColumn);
        publicationsTable.getColumns().add(yearColumn);
        publicationsTable.getColumns().add(bibtexColumn);
        publicationsTable.getColumns().add(doiColumn);

        publicationsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null) {
                if (selectionConsumer != null) {
                    selectionConsumer.accept(newValue);
                }
            } else if (emptySelectionConsumer != null) {
                emptySelectionConsumer.run();
            }

            publicationsTable.refresh();
        });

        return publicationsTable;
    }
}