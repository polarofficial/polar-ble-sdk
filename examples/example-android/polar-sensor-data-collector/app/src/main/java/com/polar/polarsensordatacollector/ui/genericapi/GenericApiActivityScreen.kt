package com.polar.polarsensordatacollector.ui.exercise

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.ui.genericapi.GenericApiViewModel

@Composable
fun GenericApiActivityScreen (
    viewModel: GenericApiViewModel
) {
    var listFilePath by remember { mutableStateOf ("") }
    var readFilePath by remember { mutableStateOf ("") }
    var writeFilePath by remember { mutableStateOf ("") }
    var writeData by remember { mutableStateOf ("") }
    var deleteFilePath by remember { mutableStateOf ("") }
    var deleteDeep by remember { mutableStateOf (false) }
    var createFolderPath by remember { mutableStateOf ("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {

        Text(
            text = stringResource(R.string.ll_api_header),
            style = MaterialTheme.typography.h6,
            color = MaterialTheme.colors.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp)
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.generic_api_generic_api_do_list_files_header),
            style = MaterialTheme.typography.h6,
            color = MaterialTheme.colors.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            TextField(
                value = listFilePath,
                onValueChange = {
                    listFilePath = it
                },
                label = { Text(text = stringResource(R.string.generic_api_do_list_files)) },
                placeholder = { Text(text = "") },
                shape = RoundedCornerShape(8.dp),
                textStyle = TextStyle(color = Color.White)
            )

            Spacer(Modifier.width(12.dp))

            Button(
                onClick = {
                    viewModel.listFiles(listFilePath, deleteDeep)
                },
                modifier = Modifier
                    .weight(1f),
                shape = RoundedCornerShape(8.dp)
            )
            {
                Text(stringResource(R.string.generic_api_button_list))
            }
        }

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
        ) {

            Text(
                text = stringResource(R.string.generic_api_generic_api_do_list_files_recurse_deep),
                style = MaterialTheme.typography.body1,
                color = MaterialTheme.colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(vertical = 2.dp)
                    .padding(top = 8.dp)
            )

            Checkbox (
                checked = deleteDeep,
                onCheckedChange = { deleteDeep = it  } )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.generic_api_generic_api_do_read_file_header),
            style = MaterialTheme.typography.h6,
            color = MaterialTheme.colors.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            TextField(
                value = readFilePath,
                onValueChange = {
                    readFilePath = it
                },
                label = { Text(text = stringResource(R.string.generic_api_do_read_file)) },
                placeholder = { Text(text = "") },
                shape = RoundedCornerShape(8.dp),
                textStyle = TextStyle(color = Color.White)
            )

            Spacer(Modifier.width(12.dp))

            Button(
                onClick = {  viewModel.readFile(readFilePath) },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 6.dp),
                shape = RoundedCornerShape(8.dp)
            ) { Text(stringResource(R.string.generic_api_button_read)) }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.generic_api_do_write_file_header),
            style = MaterialTheme.typography.h6,
            color = MaterialTheme.colors.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp)
        )

        TextField(
            value = writeFilePath,
            onValueChange = {
                writeFilePath = it
            },
            label = { Text(text = stringResource(R.string.generic_api_do_write_file_path)) },
            placeholder = { Text(text = "") },
            shape = RoundedCornerShape(8.dp),
            textStyle = TextStyle(color = Color.White)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {

            TextField(
                value = writeData,
                onValueChange = {
                    writeData = it
                },
                label = { Text(text = stringResource(R.string.generic_api_do_write_file_data)) },
                placeholder = { Text(text = "") },
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .height(200.dp),
                shape = RoundedCornerShape(8.dp),
                textStyle = TextStyle(color = Color.White)
            )

            Spacer(Modifier.width(12.dp))

            Button(
                onClick = { viewModel.writeFile(writeFilePath, writeData.toByteArray()) },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 6.dp),
                shape = RoundedCornerShape(8.dp)
            ) { Text(stringResource(R.string.generic_api_button_write)) }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.generic_api_generic_api_do_delete_file_header),
            style = MaterialTheme.typography.h6,
            color = MaterialTheme.colors.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            TextField(
                value = deleteFilePath,
                onValueChange = {
                    deleteFilePath = it
                },
                label = { Text(text = stringResource(R.string.generic_api_do_delete_file)) },
                placeholder = { Text(text = "") },
                modifier = Modifier
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                textStyle = TextStyle(color = Color.White)
            )

            Spacer(Modifier.width(12.dp))

            Button(
                onClick = { viewModel.deleteFile(deleteFilePath) },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 6.dp),
                shape = RoundedCornerShape(8.dp)
            ) { Text(stringResource(R.string.generic_api_button_delete)) }

        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.generic_api_do_create_folder_header),
            style = MaterialTheme.typography.h6,
            color = MaterialTheme.colors.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            TextField(
                value = createFolderPath,
                onValueChange = {
                    createFolderPath = it
                },
                label = { Text(text = stringResource(R.string.generic_api_do_create_folder)) },
                placeholder = { Text(text = "") },
                modifier = Modifier
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                textStyle = TextStyle(color = Color.White)
            )

            Spacer(Modifier.width(12.dp))

            Button(
                onClick = { viewModel.createFolder(createFolderPath) },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 6.dp),
                shape = RoundedCornerShape(8.dp)
            ) { Text(stringResource(R.string.generic_api_button_create_folder)) }

        }
    }
}
